package nachos.threads;

import java.io.Console;

/*
 * Test class that tests KThread.join() method
 */
public class ThreadJoinTest {
	
	public static void Test() {
		
		//initialize three test threads
		KThread t1 = new KThread(new TestRunnable());
		KThread t2 = new KThread(new TestRunnable());
		KThread t3 = new KThread(new TestRunnable());
        
		t1.setName("ThreadJoinTest Thread1");
		t2.setName("ThreadJoinTest Thread2");
		t3.setName("ThreadJoinTest Thread3");		
        
		//fork first thread
		t1.fork();
		
		//fork second thread
		t2.fork();
		
        //start third thread only after first thread is complete 
        try {
            t1.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }       
        
        t3.fork();
        
        //let all threads finish execution before finishing main thread
        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        System.out.println("All ThreadJoinTest threads are dead");
    }

}

class TestRunnable implements Runnable{

    public void run() {
        System.out.println("[ThreadJoinTest message] Thread started:::"+Thread.currentThread().getName());
        
        //we can't use Java's Thread.sleep() to simulate waiting so we'll simulate "work" like so:
        int i = 0;
        while(i++ < 999999999) { }
        while(i-- > 0) { }
        while(i++ < 999999999) { }
        while(i-- > 0) { }
        while(i++ < 999999999) { }
        while(i-- > 0) { }
        while(i++ < 999999999) { }
        while(i-- > 0) { }
        while(i++ < 999999999) { }
        while(i-- > 0) { }
        while(i++ < 999999999) { }
        while(i-- > 0) { }
        while(i++ < 999999999) { }
        
        System.out.println("[ThreadJoinTest message] Thread ended:::"+Thread.currentThread().getName());
    }		
	
}
