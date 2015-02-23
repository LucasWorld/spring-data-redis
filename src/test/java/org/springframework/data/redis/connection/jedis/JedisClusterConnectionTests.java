/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.connection.jedis;

import static org.hamcrest.core.Is.*;
import static org.hamcrest.core.IsCollectionContaining.*;
import static org.hamcrest.core.IsNull.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.data.redis.connection.DefaultSortParameters;
import org.springframework.data.redis.connection.RedisListCommands.Position;
import org.springframework.data.redis.connection.RedisNode;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

/**
 * @author Christoph Strobl
 */
public class JedisClusterConnectionTests {

	static final List<HostAndPort> CLUSTER_NODES = Arrays.asList(new HostAndPort("127.0.0.1", 6379), new HostAndPort(
			"127.0.0.1", 6380), new HostAndPort("127.0.0.1", 6381));

	static final String KEY_1 = "key1";
	static final String KEY_2 = "key2";

	static final byte[] KEY_1_BYTES = JedisConverters.toBytes(KEY_1);
	static final byte[] KEY_2_BYTES = JedisConverters.toBytes(KEY_2);

	static final String VALUE_1 = "value1";
	static final String VALUE_2 = "value2";

	static final byte[] VALUE_1_BYTES = JedisConverters.toBytes(VALUE_1);
	static final byte[] VALUE_2_BYTES = JedisConverters.toBytes(VALUE_2);

	JedisCluster nativeConnection;
	JedisClusterConnection clusterConnection;

	@BeforeClass
	public static void before() {

		Jedis jedis = new Jedis("127.0.0.1", 6379);
		String mode = JedisConverters.toProperties(jedis.info()).getProperty("redis_mode");
		jedis.close();

		Assume.assumeThat(mode, is("cluster"));
	}

	@Before
	public void setUp() throws IOException {

		nativeConnection = new JedisCluster(new HashSet<HostAndPort>(CLUSTER_NODES));
		clusterConnection = new JedisClusterConnection(this.nativeConnection);
	}

	@After
	public void tearDown() {

		for (JedisPool pool : nativeConnection.getClusterNodes().values()) {
			pool.getResource().flushDB();
		}
		nativeConnection.close();
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void shouldAllowSettingAndGettingValues() {

		clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES);
		assertThat(clusterConnection.get(KEY_1_BYTES), is(VALUE_1_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void delShouldRemoveSingleKeyCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		clusterConnection.del(KEY_1_BYTES);

		assertThat(nativeConnection.get(KEY_1), nullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void delShouldRemoveMultipleKeysCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.set(KEY_2, VALUE_2);

		clusterConnection.del(KEY_1_BYTES);
		clusterConnection.del(KEY_2_BYTES);

		assertThat(nativeConnection.get(KEY_1), nullValue());
		assertThat(nativeConnection.get(KEY_2), nullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void keysShouldReturnAllKeys() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.set(KEY_2, VALUE_2);

		assertThat(clusterConnection.keys(JedisConverters.toBytes("*")), hasItems(KEY_1_BYTES, KEY_2_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void randomKeyShouldReturnCorrectlyWhenKeysAvailable() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.set(KEY_2, VALUE_2);

		assertThat(clusterConnection.randomKey(), notNullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void randomKeyShouldReturnNullWhenNoKeysAvailable() {
		assertThat(clusterConnection.randomKey(), nullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void expireShouldBeSetCorreclty() {

		nativeConnection.set(KEY_1, VALUE_1);

		clusterConnection.expire(KEY_1_BYTES, 5);

		assertThat(nativeConnection.ttl(JedisConverters.toString(KEY_1_BYTES)) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void pExpireShouldBeSetCorreclty() {

		nativeConnection.set(KEY_1, VALUE_1);

		clusterConnection.pExpire(KEY_1_BYTES, 5000);

		assertThat(nativeConnection.ttl(JedisConverters.toString(KEY_1_BYTES)) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void expireAtShouldBeSetCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		clusterConnection.expireAt(KEY_1_BYTES, System.currentTimeMillis() / 1000 + 5000);

		assertThat(nativeConnection.ttl(JedisConverters.toString(KEY_1_BYTES)) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void pExpireAtShouldBeSetCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		clusterConnection.pExpireAt(KEY_1_BYTES, System.currentTimeMillis() + 5000);

		assertThat(nativeConnection.ttl(JedisConverters.toString(KEY_1_BYTES)) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void dbSizeShouldReturnCummulatedDbSize() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.set(KEY_2, VALUE_2);

		assertThat(clusterConnection.dbSize(), is(2L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void dbSizeForSpecificNodeShouldGetNodeDbSize() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.set(KEY_2, VALUE_2);

		assertThat(clusterConnection.dbSize(new RedisNode("127.0.0.1", 6379)), is(1L));
		assertThat(clusterConnection.dbSize(new RedisNode("127.0.0.1", 6380)), is(1L));
		assertThat(clusterConnection.dbSize(new RedisNode("127.0.0.1", 6381)), is(0L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test(expected = UnsupportedOperationException.class)
	public void moveShouldThrowExeption() {
		clusterConnection.move("".getBytes(), 2);
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void ttlShouldReturnMinusTwoWhenKeyDoesNotExist() {
		assertThat(clusterConnection.ttl(KEY_1_BYTES), is(-2L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void ttlShouldReturnMinusOneWhenKeyDoesNotHaveExpirationSet() {

		nativeConnection.set(KEY_1, VALUE_1);

		assertThat(clusterConnection.ttl(KEY_1_BYTES), is(-1L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void ttlShouldReturValueCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.expire(KEY_1, 5);

		assertThat(clusterConnection.ttl(KEY_1_BYTES) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void pTtlShouldReturnMinusTwoWhenKeyDoesNotExist() {
		assertThat(clusterConnection.pTtl(KEY_1_BYTES), is(-2L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void pTtlShouldReturnMinusOneWhenKeyDoesNotHaveExpirationSet() {

		nativeConnection.set(KEY_1, VALUE_1);

		assertThat(clusterConnection.pTtl(KEY_1_BYTES), is(-1L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void pTtlShouldReturValueCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.expire(KEY_1, 5);

		assertThat(clusterConnection.pTtl(KEY_1_BYTES) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	@Ignore("check this one later - does not work")
	public void sortShouldReturValuesCorrectly() {

		nativeConnection.lpush(KEY_1, VALUE_2, VALUE_1);

		assertThat(clusterConnection.sort(KEY_1_BYTES, new DefaultSortParameters().asc()),
				hasItems(VALUE_1_BYTES, VALUE_2_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void dumpAndRestoreShouldWorkCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		byte[] dumpedValue = clusterConnection.dump(KEY_1_BYTES);
		clusterConnection.restore(KEY_2_BYTES, 0, dumpedValue);

		assertThat(nativeConnection.get(KEY_2), is(VALUE_1));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void getShouldReturnValueCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		assertThat(clusterConnection.get(KEY_1_BYTES), is(VALUE_1_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void getSetShouldWorkCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		byte[] valueBeforeSet = clusterConnection.getSet(KEY_1_BYTES, VALUE_2_BYTES);

		assertThat(valueBeforeSet, is(VALUE_1_BYTES));
		assertThat(nativeConnection.get(KEY_1), is(VALUE_2));
	}

	@Test
	@Ignore
	public void mGetShouldReturnCorrectlyWhenKeysMapToSameSlot() {
		// TODO manually map keys to a fixed same slot
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void setShouldSetValueCorrectly() {

		clusterConnection.set(KEY_1_BYTES, VALUE_1_BYTES);

		assertThat(nativeConnection.get(KEY_1), is(VALUE_1));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void setNxShouldSetValueCorrectly() {

		clusterConnection.setNX(KEY_1_BYTES, VALUE_1_BYTES);

		assertThat(nativeConnection.get(KEY_1), is(VALUE_1));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void setNxShouldNotSetValueWhenAlreadyExistsInDBCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		clusterConnection.setNX(KEY_1_BYTES, VALUE_2_BYTES);

		assertThat(nativeConnection.get(KEY_1), is(VALUE_1));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void setExShouldSetValueCorrectly() {

		clusterConnection.setEx(KEY_1_BYTES, 5, VALUE_1_BYTES);

		assertThat(nativeConnection.get(KEY_1), is(VALUE_1));
		assertThat(nativeConnection.ttl(KEY_1) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void pSetExShouldSetValueCorrectly() {

		clusterConnection.pSetEx(KEY_1_BYTES, 5000, VALUE_1_BYTES);

		assertThat(nativeConnection.get(KEY_1), is(VALUE_1));
		assertThat(nativeConnection.ttl(KEY_1) > 1, is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	@Ignore
	public void mSetShouldWorkOnceKeysMapToSameSlot() {

	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	@Ignore
	public void mSetNXShouldWorkOnceKeysMapToSameSlot() {

	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void incrShouldIncreaseValueCorrectly() {

		nativeConnection.set(KEY_1, "1");

		assertThat(clusterConnection.incr(KEY_1_BYTES), is(2L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void incrByShouldIncreaseValueCorrectly() {

		nativeConnection.set(KEY_1, "1");

		assertThat(clusterConnection.incrBy(KEY_1_BYTES, 5), is(6L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void decrShouldDecreaseValueCorrectly() {

		nativeConnection.set(KEY_1, "5");

		assertThat(clusterConnection.decr(KEY_1_BYTES), is(4L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void decrByShouldDecreaseValueCorrectly() {

		nativeConnection.set(KEY_1, "5");

		assertThat(clusterConnection.decrBy(KEY_1_BYTES, 4), is(1L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void appendShouldAddValueCorrectly() {

		clusterConnection.append(KEY_1_BYTES, VALUE_1_BYTES);
		clusterConnection.append(KEY_1_BYTES, VALUE_2_BYTES);

		assertThat(nativeConnection.get(KEY_1), is(VALUE_1.concat(VALUE_2)));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void getRangeShouldReturnValueCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		assertThat(clusterConnection.getRange(KEY_1_BYTES, 0, 2), is(JedisConverters.toBytes("val")));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void setRangeShouldWorkCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		clusterConnection.setRange(KEY_1_BYTES, JedisConverters.toBytes("UE"), 3);

		assertThat(nativeConnection.get(KEY_1), is("valUE1"));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void strLenShouldWorkCorrectly() {

		nativeConnection.set(KEY_1, VALUE_1);

		assertThat(clusterConnection.strLen(KEY_1_BYTES), is(6L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void rPushShoultAddValuesCorrectly() {

		clusterConnection.rPush(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES);

		assertThat(nativeConnection.lrange(KEY_1, 0, -1), hasItems(VALUE_1, VALUE_2));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lPushShoultAddValuesCorrectly() {

		clusterConnection.lPush(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES);

		assertThat(nativeConnection.lrange(KEY_1, 0, -1), hasItems(VALUE_1, VALUE_2));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void rPushNXShoultNotAddValuesWhenKeyDoesNotExist() {

		clusterConnection.rPushX(KEY_1_BYTES, VALUE_1_BYTES);

		assertThat(nativeConnection.exists(KEY_1), is(false));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lPushNXShoultNotAddValuesWhenKeyDoesNotExist() {

		clusterConnection.lPushX(KEY_1_BYTES, VALUE_1_BYTES);

		assertThat(nativeConnection.exists(KEY_1), is(false));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lLenShouldCountValuesCorrectly() {

		nativeConnection.lpush(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.lLen(KEY_1_BYTES), is(2L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lRangeShouldGetValuesCorrectly() {

		nativeConnection.lpush(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.lRange(KEY_1_BYTES, 0L, -1L), hasItems(VALUE_1_BYTES, VALUE_2_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lTrimShouldTrimListCorrectly() {

		nativeConnection.lpush(KEY_1, VALUE_1, VALUE_2, "foo", "bar");

		clusterConnection.lTrim(KEY_1_BYTES, 2, 3);

		assertThat(nativeConnection.lrange(KEY_1, 0, -1), hasItems(VALUE_1, VALUE_2));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lIndexShouldGetElementAtIndexCorrectly() {

		nativeConnection.rpush(KEY_1, VALUE_1, VALUE_2, "foo", "bar");

		assertThat(clusterConnection.lIndex(KEY_1_BYTES, 1), is(VALUE_2_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lInsertShouldAddElementAtPositionCorrectly() {

		nativeConnection.rpush(KEY_1, VALUE_1, VALUE_2, "foo", "bar");

		clusterConnection.lInsert(KEY_1_BYTES, Position.AFTER, VALUE_2_BYTES, JedisConverters.toBytes("booh!"));

		assertThat(nativeConnection.lrange(KEY_1, 0, -1).get(2), is("booh!"));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lSetShouldSetElementAtPositionCorrectly() {

		nativeConnection.rpush(KEY_1, VALUE_1, VALUE_2, "foo", "bar");

		clusterConnection.lSet(KEY_1_BYTES, 1L, VALUE_1_BYTES);

		assertThat(nativeConnection.lrange(KEY_1, 0, -1).get(1), is(VALUE_1));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lRemShouldRemoveElementAtPositionCorrectly() {

		nativeConnection.rpush(KEY_1, VALUE_1, VALUE_2, "foo", "bar");

		clusterConnection.lRem(KEY_1_BYTES, 1L, VALUE_1_BYTES);

		assertThat(nativeConnection.llen(KEY_1), is(3L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void lPopShouldReturnElementCorrectly() {

		nativeConnection.rpush(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.lPop(KEY_1_BYTES), is(VALUE_1_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void rPopShouldReturnElementCorrectly() {

		nativeConnection.rpush(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.rPop(KEY_1_BYTES), is(VALUE_2_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	@Ignore
	public void rPopLPushShouldWorkWhenKeysMapToSameSlot() {
		// TODO check slots and assing manually
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	@Ignore
	public void bRPopLPushShouldWorkWhenKeysMapToSameSlot() {
		// TODO check slots and assing manually
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sAddShouldAddValueToSetCorrectly() {

		clusterConnection.sAdd(KEY_1_BYTES, VALUE_1_BYTES, VALUE_2_BYTES);

		assertThat(nativeConnection.smembers(KEY_1), hasItems(VALUE_1, VALUE_2));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sRemShouldRemoveValueFromSetCorrectly() {

		nativeConnection.sadd(KEY_1, VALUE_1, VALUE_2);

		clusterConnection.sRem(KEY_1_BYTES, VALUE_2_BYTES);

		assertThat(nativeConnection.smembers(KEY_1), hasItems(VALUE_1));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sPopShouldPopValueFromSetCorrectly() {

		nativeConnection.sadd(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.sPop(KEY_1_BYTES), notNullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	@Ignore
	public void sMoveShouldWorkWhenKeysMapToSameSlot() {
		// TODO check slots and assing manually
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sCardShouldCountValuesInSetCorrectly() {

		nativeConnection.sadd(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.sCard(KEY_1_BYTES), is(2L));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sIsMemberShouldReturnTrueIfValueIsMemberOfSet() {

		nativeConnection.sadd(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.sIsMember(KEY_1_BYTES, VALUE_1_BYTES), is(true));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sIsMemberShouldReturnFalseIfValueIsMemberOfSet() {

		nativeConnection.sadd(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.sIsMember(KEY_1_BYTES, JedisConverters.toBytes("foo")), is(false));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sMembersShouldReturnValuesContainedInSetCorrectly() {

		nativeConnection.sadd(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.sMembers(KEY_1_BYTES), hasItems(VALUE_1_BYTES, VALUE_2_BYTES));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void sRandMamberShouldReturnValueCorrectly() {

		nativeConnection.sadd(KEY_1, VALUE_1, VALUE_2);

		assertThat(clusterConnection.sRandMember(KEY_1_BYTES), notNullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	@Ignore
	public void sscanShouldRetrieveAllValuesInSetCorrectly() {
		// TODO implement it
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void pingShouldRetrunPongForExistingNode() {
		assertThat(clusterConnection.ping(new RedisNode("127.0.0.1", 6379)), is("PONG"));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test(expected = IllegalArgumentException.class)
	public void pingShouldThrowExceptionWhenNodeNotKnownToCluster() {
		clusterConnection.ping(new RedisNode("127.0.0.1", 1234));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void flushDbShouldFlushAllClusterNodes() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.set(KEY_2, VALUE_2);

		clusterConnection.flushDb();

		assertThat(nativeConnection.get(KEY_1), nullValue());
		assertThat(nativeConnection.get(KEY_2), nullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void flushDbOnSingleNodeShouldFlushOnlyGivenNodesDd() {

		nativeConnection.set(KEY_1, VALUE_1);
		nativeConnection.set(KEY_2, VALUE_2);

		clusterConnection.flushDb(new RedisNode("127.0.0.1", 6379));

		assertThat(nativeConnection.get(KEY_1), notNullValue());
		assertThat(nativeConnection.get(KEY_2), nullValue());
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void infoShouldCollectionInfoFromAllClusterNodes() {
		assertThat(clusterConnection.info().size(), is(3));
	}

	/**
	 * @see DATAREDIS-315
	 */
	@Test
	public void clientListShouldGetInfosForAllClients() {
		assertThat(clusterConnection.getClientList().isEmpty(), is(false));
	}

	@Test
	public void getClusterNodeForKeyShouldReturnNodeCorrectly() {
		assertThat((RedisNode) clusterConnection.getClusterNodeForKey(KEY_1_BYTES), is(new RedisNode("127.0.0.1", 6380)));
	}
}