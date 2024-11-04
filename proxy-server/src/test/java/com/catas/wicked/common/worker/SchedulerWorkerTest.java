package com.catas.wicked.common.worker;

import com.catas.wicked.common.executor.ScheduledThreadPoolService;
import org.junit.Assert;
import org.junit.Test;


public class SchedulerWorkerTest {

    @Test
    public void testScheduler() throws InterruptedException {
        final int[] cnt = {0};
        AbstractScheduledWorker worker = new AbstractScheduledWorker() {
            long time = System.currentTimeMillis();

            @Override
            public long getDelay() {
                return 2 * 1000;
            }

            @Override
            protected boolean doWork(boolean manually) {
                // long cost = System.currentTimeMillis() - time;
                // System.out.println("Running task: " + cnt[0] + " manually: " + manually + " time: " + cost);
                cnt[0]++;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
                return true;
            }
        };

        /**
         * 0: auto(fail), man-(success)
         * 0.6: man
         * 0.8: man
         * 2: auto
         * 4: auto
         * total: 5 times
         */
        ScheduledThreadPoolService.getInstance().submit(worker, worker.getDelay());
        worker.invoke();
        Thread.sleep(100);
        worker.start();

        Thread.sleep(500);
        worker.invoke();
        worker.invoke();

        Thread.sleep(4000);
        Assert.assertEquals(5, cnt[0]);
    }
}
