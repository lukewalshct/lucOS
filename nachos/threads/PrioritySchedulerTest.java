package nachos.threads;

import nachos.machine.*;

/*
 * A class that tests PriorityScheduler.java.
 * 
 * TODO: add documentation
 */
public class PrioritySchedulerTest extends KernelTestBase {
	
	private final int NUM_PRIORITIES = 8;
	
	public void RunTests()
	{
		System.out.println("\n\nRunning PriorityScheduler tests...\n");
		
		PriorityScheduler scheduler = (PriorityScheduler) ThreadedKernel.scheduler;
		
		runLockTest(scheduler, 20);
		
		System.out.println("\n\nPriorityScheduler tests complete.\n");
	}
	
	private void runLockTest(PriorityScheduler scheduler, int numThreads)
	{
		System.out.println("entering lock test");
		
		Lock lock = new Lock();
		
		KThread[] testThreads = new KThread[numThreads];
		
		for(int i = 0; i < numThreads; i++)
		{
			int priority = i % NUM_PRIORITIES;
			
			testThreads[i] = new KThread(new TestThread(i, lock, priority));
			
			boolean intStatus = Machine.interrupt().disable();
			
			scheduler.setPriority(testThreads[i], priority);
			
			Machine.interrupt().restore(intStatus);
		}

		//fork all threads
		runThreads(testThreads);
		
		try{
			joinThreads(testThreads);
		}catch(Exception ex) {}
		
		System.out.println("exiting lock test");
	}
	
	private class TestThread implements Runnable
	{
		private int _priority;
		
		private int _threadNum;
		
		private Lock _lock;
		
		public TestThread(int threadNum, Lock lock, int priority)
		{
			_threadNum = threadNum;
			
			_lock = lock;
			
			_priority = priority;
		}
		
		public void run()
		{
			GetLock();
		}
		
		public void GetLock()
		{
			System.out.println("Thread #" + _threadNum + " attempting to acquire lock. Priority is " + _priority);
			
			_lock.acquire();			
			
			System.out.println("Thread #" + _threadNum + " acquired lock. Priority is " + _priority);
			
			_lock.release();
		}
		
		private void simulateWork()
		{
			for(int i = 0; i < 3; i++)
			{
				KThread.yield();
			}
		}
		
	}

}
