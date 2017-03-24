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
		Alarm alarm = new Alarm();
		
		KThread t1 = new KThread(new Runnable() { 
			public void run() { AssertWaitTimeIsCorrect(alarm, 10000); } 
			});
		KThread t2 = new KThread(new Runnable() { 
			public void run() { AssertWaitTimeIsCorrect(alarm, 30000); } 
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
	
	/*
	 * Calls the alarm to wait the specified clock cycles, and then 
	 * asserts that the correct amount of cycles (or more) have passed.
	 */
	private void AssertWaitTimeIsCorrect(Alarm alarm, long clockCycles)
	{
		
		String tName = KThread.currentThread().getName();
		
		long curTime1 = Machine.timer().getTime();
		
		//wait
		alarm.waitUntil(clockCycles);
		
		long curTime2 = Machine.timer().getTime();
		
		//assert the waitin was correct (>= clockcycles requested to wait)
		Lib.assertTrue((curTime2 - curTime1) >= clockCycles);
		
		System.out.println(tName + "\nWait requested (clock cycles): " +
			clockCycles + "\nTime before wait: " + curTime1 +
			"\nTime after wait: " + curTime2 + "\nTime elapsed: " + (curTime2 -curTime1));
	}
}
