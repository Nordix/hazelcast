package com.hazelcast.transaction.impl;

import com.hazelcast.cluster.Address;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.test.Accessors;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.internal.util.UuidUtil;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import static com.hazelcast.transaction.TransactionOptions.TransactionType.TWO_PHASE;

/**
 * TEMPORARY CI diagnostic — not intended to stay. Reproduces the inputs of
 * prepare_whenMultipleItemsAndDurabilityOne_thenBackupLog and prints the
 * values that decide whether backup-log ops are sent at all.
 */
@RunWith(HazelcastParallelClassRunner.class)
@Category(QuickTest.class)
public class TxBackupDiagIT extends HazelcastTestSupport {

    @Override
    protected com.hazelcast.config.Config getConfig() {
        com.hazelcast.config.Config config = new com.hazelcast.config.Config();
        config.getSerializationConfig().addDataSerializableFactory(
                MockTransactionLogRecord.MockTransactionLogRecordSerializerHook.F_ID,
                new MockTransactionLogRecord.MockTransactionLogRecordSerializerHook().createFactory());
        return config;
    }

    @Test
    public void diag() throws Exception {
        HazelcastInstance[] cluster = createHazelcastInstanceFactory(2).newInstances(getConfig());
        Node localNode = Accessors.getNode(cluster[0]);
        Node remoteNode = Accessors.getNode(cluster[1]);
        NodeEngineImpl localEngine = localNode.getNodeEngine();
        TransactionManagerServiceImpl localTx =
                (TransactionManagerServiceImpl) localEngine.getTransactionManagerService();
        TransactionManagerServiceImpl remoteTx =
                (TransactionManagerServiceImpl) Accessors.getNode(cluster[1]).getNodeEngine().getTransactionManagerService();

        System.out.println("DIAG localClusterSize=" + localEngine.getClusterService().getSize()
                + " remoteClusterSize=" + remoteNode.getNodeEngine().getClusterService().getSize());
        com.hazelcast.internal.serialization.InternalSerializationService ssc =
                (com.hazelcast.internal.serialization.InternalSerializationService) Accessors.getNode(cluster[1]).getSerializationService();
        try {
            java.lang.reflect.Field f = com.hazelcast.internal.serialization.impl.AbstractSerializationService.class
                    .getDeclaredField("dataSerializableSerializers");
            f.setAccessible(true);
            java.util.Map<?, ?> m2 = (java.util.Map<?, ?>) f.get(ssc);
            System.out.println("DIAG remoteFactoryIds=" + m2.keySet());
        } catch (Exception e) {
            System.out.println("DIAG factoryDumpFailed=" + e);
        }
        System.out.println("DIAG localAddr=" + localNode.getThisAddress()
                + " remoteAddr=" + remoteNode.getThisAddress());
        System.out.println("DIAG pickBackupLogAddresses(1)="
                + Arrays.toString(localTx.pickBackupLogAddresses(1)));

        TransactionOptions options = new TransactionOptions().setTransactionType(TWO_PHASE).setDurability(1);
        UUID txOwner = UuidUtil.newUnsecureUUID();
        TransactionImpl tx = new TransactionImpl(localTx, localEngine, options, txOwner);
        tx.begin();
        tx.add(new MockTransactionLogRecord());
        tx.add(new MockTransactionLogRecord());
        Field ba = TransactionImpl.class.getDeclaredField("backupAddresses");
        ba.setAccessible(true);
        tx.prepare();
        System.out.println("DIAG state=" + tx.getState() + " txnId=" + tx.getTxnId()
                + " txBackupAddresses=" + Arrays.toString((Address[]) ba.get(tx)));
        System.out.println("DIAG immediately remoteHas=" + remoteTx.txBackupLogs.containsKey(tx.getTxnId()));
        Thread.sleep(5000);
        System.out.println("DIAG after5s remoteHas=" + remoteTx.txBackupLogs.containsKey(tx.getTxnId())
                + " remoteLogKeys=" + remoteTx.txBackupLogs.keySet());
        tx.rollback();
    }
}
