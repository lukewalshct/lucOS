package nachos.threads;

import nachos.machine.*;
import java.lang.Math;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    
	//lock that must be held in order to add threads to wait queue
	private Lock _queueLock;
	
	private PriorityQueue<KThreadWaiting> _waitQueue;
	/**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
    	
    	_queueLock = new Lock();
    	
    	_waitQueue = new PriorityQueue<KThreadWaiting>();
    	
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
    	
    	//place any threads that have passed their wait time
    	//onto the ready queue
    	Machine.interrupt().disable();
    	
    	_queueLock.acquire();  	
    	
    	while(_waitQueue.peek() != null && 
    			_waitQueue.peek().WakeTime <= Machine.timer().getTime())
    	{
    		KThreadWaiting threadToWake = _waitQueue.poll();
    		
    		threadToWake.WaitingThread.ready();
    	}
    	
    	_queueLock.release();
    	
    	Machine.interrupt().enable();
    	
    	//yield current thread
    	KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
    	
    	_queueLock.acquire();   	
    	
    	KThread curThread = KThread.currentThread();
    	
    	//get time to be worken
    	long wakeTime = Machine.timer().getTime() + x;
    	
    	KThreadWaiting threadToWait = new KThreadWaiting(curThread, wakeTime);
		
    	//add thread to queue waiting for timer interrupt
    	_waitQueue.add(threadToWait);
    	
    	_queueLock.release();
    	
    	Machine.interrupt().disable();
    	
    	//sleep thread - will be woken by timer interrupt
    	KThread.sleep();
    }
    
    /*
     * Class that represents a KThread that is waiting. We use this
     * new class to be able to use a comparator for the priority queue.
     * 
     * The WakeTime represents the number of clock cycles at which point
     * the thread should be awoken from waiting. This will be used to 
     * maintain sorted priority in the queue.
     */
    private class KThreadWaiting implements Comparable<KThreadWaiting>
    {
    	//the waiting thread
    	public KThread WaitingThread;
    	
    	//the machine timer's number of cycles to wait unil (note NOT
    	//the same as the value passed into waitUntil()
    	public long WakeTime;

    	/*
    	 * Constructor
    	 */
    	public KThreadWaiting(KThread thread, long wakeTime)
    	{
    		WaitingThread = thread;
    		
    		WakeTime = wakeTime;
    	}
    	
    	/*
    	 * Compares to another KThreadWaiting, based on the wake 
    	 * time)
    	 */
    	@Override
    	public int compareTo(KThreadWaiting other)
    	{
    		//returns sign of subtraction (-1, 0, or 1)
    		return (int) Math.signum(this.WakeTime - other.WakeTime);    		
    	}
    	
    }
}
