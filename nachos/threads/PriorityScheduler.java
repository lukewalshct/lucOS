package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	
	private java.util.PriorityQueue<ThreadState> waitQueue;
	
	//represents the thread that actively holds the resource (i.e. not on the queue)
	private ThreadState activeThreadState;
	
	//represents the maximum effective priority of the queue
	private int maxEffPriority;
	
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
		
		int numPriorities = (priorityMaximum - priorityMinimum) + 1;

		this.waitQueue = new java.util.PriorityQueue<ThreadState>();
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	/**
	 * Adds a new thread to the wait queue. If the new thread's effective
	 * priority is greater than the active thread's effective priority,
	 * update the active thread's effective priority
	 */
	protected void add(ThreadState threadState) 
	{		
		boolean intStatus = Machine.interrupt().disable();

		//get the thread that's currently next in line
		ThreadState origNextThread = this.waitQueue.peek();
		
		//add the new thread state to the queue
		this.waitQueue.add(threadState); 			
		
		//handle priority donation
		updateActiveThreadEffectivePriority(origNextThread);

		Machine.interrupt().restore(intStatus);
	}
	
	private void updateActiveThreadEffectivePriority(ThreadState origNextThread)
	{
		if(!this.transferPriority) return;
		
		//get the active thread
		ThreadState activeThreadState = this.activeThreadState;
		
		//get the next thread in line
		ThreadState firstInLineThreadState = this.waitQueue.peek();
		
		//if the next thread in line is equal to the "original" next thread, 
		//no update is needed so just return
		if(origNextThread == firstInLineThreadState || activeThreadState == null)
		{
			return;
		}
		else
		{
			//remove the original next thread from the active thread's donor list
			if(origNextThread != null) activeThreadState.removeDonor(origNextThread);
			
			//add the new highest priority thread to 
			activeThreadState.addDonor(firstInLineThreadState);
		}
	}
	
	/**
	 * Updates the priority queue's active thread state
	 * to the new one.
	 */
	protected void setActiveThreadState(ThreadState ts)
	{
		this.activeThreadState = ts;
	}
	
	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
		
	    // implement me
		int curPriority = priorityMaximum;
		
		System.out.println("q length: " + this.waitQueue.size());
		
		System.out.println("t waiting: ");
		
		Iterator it = this.waitQueue.iterator();
		
		while (it.hasNext())
		{
			System.out.print(((ThreadState)it.next()).thread.getName() + " ");
		}
		
		ThreadState ts = this.waitQueue.poll();
		
		if (ts != null)
		{
			KThread t = ts.thread;
			
			System.out.println("\nnext up: " + t.getName());

			return t;
		}
		
		return null;
	}
	
	/**
	 * If a thread's priority has been updated, notify this queue
	 * so that the effective priority of the thread holding the 
	 * resource may be updated if necessary
	 */
	public void notifyPriorityUpdate(ThreadState threadState)
	{
		//TODO: implement
	}
	
	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    // implement me
		
		Lib.assertTrue(Machine.interrupt().disabled()); //temp to mimic RR
		 
		return this.waitQueue.peek();		    
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState implements Comparable<ThreadState>{
			
	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	/**The priority queue the thread is waiting on (null if it isn't) */
	protected PriorityQueue waitQueue;
	/** Represents a max-effective-priority-on-top heap of threads donating priority*/
	private java.util.PriorityQueue<ThreadState> donorThreads;
	
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread)
	{
	    this.thread = thread;
	    
	    setPriority(priorityDefault);
		
		this.donorThreads = new java.util.PriorityQueue<ThreadState>();
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {

		ThreadState highestDonor = this.donorThreads.peek();

		if(highestDonor != null)
		{
			int highestDonorPriority = highestDonor.getEffectivePriority(); //TODO: cache eff priority to optimize
			
			return this.priority > highestDonorPriority ? this.priority : highestDonorPriority; 
		}
		else
		{
			return this.priority;
		}	    
	}

	protected void addDonor(ThreadState threadState)
	{
		this.donorThreads.add(threadState);
	}
	
	protected void removeDonor(ThreadState threadState)
	{
		this.donorThreads.remove(threadState);
	}
	
	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
		boolean priorityChanged = this.priority != priority;
		
	    this.priority = priority;
	    
		//if the thread is on a wait queue that allows for priority donation,
		//notify the queue that the thread's priority changed
		if(priorityChanged && this.waitQueue != null && this.waitQueue.transferPriority)
		{
			this.waitQueue.notifyPriorityUpdate(this);
		}  
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    
		this.waitQueue = waitQueue;
		
		waitQueue.add(this);	
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    // implement me     
		waitQueue.setActiveThreadState(this);	    
	}

	@Override
	public int compareTo(ThreadState other)
	{
		if (this.getEffectivePriority() > other.getEffectivePriority())
		{
			return -1;
		}
		else if (this.getEffectivePriority() < other.getEffectivePriority())
		{
			return 1;
		}
		else
		{
			return 0;
		}
	}	
    }
}
