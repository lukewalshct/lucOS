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
		
		runLockTest1(scheduler, 20);
		
		runLockTest2(scheduler);
		
		System.out.println("\n\nPriorityScheduler tests complete.\n");
	}
	
	private void runLockTest1(PriorityScheduler scheduler, int numThreads)
	{
		System.out.println("entering lock test 1");
		
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
		
		System.out.println("exiting lock test 1");
	}
	
	private void runLockTest2(PriorityScheduler scheduler)
	{
		System.out.println("\nentering lock test 2");
		
		Lock lock = new Lock();		
				
		//create three new low priority threads that will try to access lock
		KThread t1 = new KThread(new TestThread(0, lock, 0));
		KThread t2 = new KThread(new TestThread(1, lock, 0));
		KThread t3 = new KThread(new TestThread(2, lock, 0));
		
		//fork low priority threads
		t1.fork();
		t2.fork();
		t3.fork();
		
		//create high priority thread that will try to access lock
		KThread t4 = new KThread(new TestThread(3, lock, 5));
		
		//run high priority thread (result is that it should donate its priority to 
		//the low priority lock holding the thread)
		t4.fork();
		
		//join on all threads
		try{
			t1.join();
			t2.join();
			t3.join();
			t4.join();	
		}catch(Exception ex) {}

		
		System.out.println("\nexiting lock test 2");
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
			_lock.acquire();			
			
			boolean intStatus = Machine.interrupt().disable();
	
			System.out.println("Thread #" + _threadNum + 
				" acquired lock. \nPriority is " + _priority + 
				"\nEff priority is " + ThreadedKernel.scheduler.getEffectivePriority(KThread.currentThread()));
			
			Machine.interrupt().restore(intStatus);		
			
			simulateWork();
			
			_lock.release();
		}
		
		private void simulateWork()
		{
			int numWork = 10;
			
			for(int i = 0; i < numWork; i++)
			{
				boolean intStatus = Machine.interrupt().disable();
				
				System.out.println("Thread #" + _threadNum + 
				" doing work (round " + (i + 1) + " of " + numWork +")" +
				"\nPriority is " + _priority + 
				"\nEff priority is " + ThreadedKernel.scheduler.getEffectivePriority(KThread.currentThread()));
				
				Machine.interrupt().restore(intStatus);
				
				KThread.yield();
			}
		}
		
	}

}
