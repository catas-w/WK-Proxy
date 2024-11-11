package com.catas.wicked.common.worker;

import com.catas.wicked.BaseTest;
import com.catas.wicked.common.executor.ThreadPoolService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScheduledManagerTest extends BaseTest {

    @Test
    public void testScheduledManager() throws InterruptedException {
        Count autoCnt = new Count();
        Count manualCnt = new Count();
        ScheduledManager manager = new ScheduledManager();
        ScheduledWorker worker = new AbstractScheduledWorker() {
            @Override
            public long getDelay() {
                return 1000;
            }

            @Override
            protected boolean doWork(boolean manually) {
                // System.out.println("running task");
                if (manually) {
                    manualCnt.add(1);
                } else {
                    autoCnt.add(1);
                }
                return true;
            }
        };

        String worker1 = "worker1";
        manager.register(worker1, worker);
        Thread.sleep(2500);

        manager.invoke(worker1);
        manager.cancel(worker1);
        Thread.sleep(2000);

        Assertions.assertEquals(1, manualCnt.count);
        Assertions.assertEquals(3, autoCnt.count);
    }

    @Test
    public void testPause() throws InterruptedException {
        Count autoCnt = new Count();
        ScheduledManager manager = new ScheduledManager();
        ScheduledWorker worker = new AbstractScheduledWorker() {
            @Override
            public long getDelay() {
                return 1000;
            }

            @Override
            protected boolean doWork(boolean manually) {
                System.out.println("---------- running task ----------- " + System.currentTimeMillis());
                autoCnt.add(1);
                return true;
            }
        };

        String worker1 = "worker1";
        manager.register(worker1, worker);
        Thread.sleep(2500);

        manager.pause(worker1);
        Thread.sleep(2800);

        manager.resume(worker1);
        Thread.sleep(1800);

        Assertions.assertEquals(5, autoCnt.count);
    }

    @Test
    public void testManagerErrors() {
        Count cnt = new Count();
        ScheduledManager manager = new ScheduledManager();
        ScheduledWorker worker = new AbstractScheduledWorker() {
            @Override
            public long getDelay() {
                return 1000;
            }

            @Override
            protected boolean doWork(boolean manually) {
                cnt.add(1);
                return true;
            }
        };


        manager.register("duplicate", worker);
        Assertions.assertThrows(IllegalArgumentException.class, () -> manager.register("duplicate", null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> manager.invoke("not-exist"));
    }

    @Test
    public void testScheduledManagerInvoke() throws InterruptedException {
        Count asyncCnt = new Count();
        Count totalCnt = new Count();
        ScheduledManager manager = new ScheduledManager();
        ScheduledWorker worker = new AbstractScheduledWorker() {
            final long stat = System.currentTimeMillis();

            @Override
            public long getDelay() {
                return 2000;
            }

            @Override
            protected long getLockTimeout() {
                return 5;
            }

            @Override
            protected boolean doWork(boolean manually) {
                long time = System.currentTimeMillis() - stat;
                System.out.println(time + " Manually: " + manually + ", " + Thread.currentThread().getName());
                totalCnt.add(1);
                if (ThreadPoolService.owns(Thread.currentThread().getName())) {
                    asyncCnt.add(1);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                return true;
            }
        };

        String worker1 = "worker1";
        // success +1
        manager.register(worker1, worker);
        Thread.sleep(200);

        // success +1
        manager.invoke(worker1);
        Thread.sleep(200);

        // success +1
        manager.invokeAsync(worker1);
        // failed on lock
        manager.invokeAsync(worker1);
        Thread.sleep(200);

        Assertions.assertEquals(1, asyncCnt.count);
        Assertions.assertEquals(3, totalCnt.count);
    }

    static class Count {
        public volatile int count;

        public synchronized void add(int amount) {
            count += amount;
        }
    }
}
