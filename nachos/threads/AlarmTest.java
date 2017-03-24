package nachos.threads;

import nachos.machine.*;

/*
 * A class that tests Alarm.java, specificy the waitUntil method,
 * by creating several threads, calling to waitUntil, and then 
 * checking each thread when it's re-awoken that at least the time
 * specified has passed.
 */
public class AlarmTest {

	public void TestWaitUntil()
	{
		KThread t1 = new KThread(new Runnable() { 
			public void run() { AssertWaitTimeIsCorrect(10000); } 
			});
		KThread t2 = new KThread(new Runnable() { 
			public void run() { AssertWaitTimeIsCorrect(30000); } 
			});
		
		t1.setName("ALARM TEST Thread 1");
		t2.setName("ALARM TEST Thread 2");
		
		t1.fork();
		t2.fork();
		
		try
		{
			t1.join();
			t2.join();
		}catch(Exception e){}
	}
	
	private void AssertWaitTimeIsCorrect(long clockCycles)
	{
		String tName = KThread.currentThread().getName();
		
		long curTime = Machine.timer().getTime();
		
		System.out.println(tName + "\nWait requested (clock cycles): " +
			clockCycles + "\nCurrent time (ticks since machine boot): " + curTime);
	}
}
