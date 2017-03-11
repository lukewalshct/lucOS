package nachos.threads;

/*
 * A class that tests both Condition.java and Condition2.java
 * by using a synchronized queue that utilizes these condition 
 * variables. Tests to see if consumer/producer threads work
 * together using each condition variable implementation.
 */

import java.util.*;

public class ConditionTest {
	
	public void TestCondition(){
		//Set up synchronized queue
		Lock lock = new Lock();
		ICondition c = new Condition(lock);
		SyncQueue queue = new SyncQueue(lock, c);
		
		//set up producer threads
		KThread[] producers = getProducers(10);
		
		//set up consumer threads
		KThread[] consumers = getConsumers(5);
		
		runThreads(consumers);
		
		runThreads(producers);
	}
	
	public void TestCondition2(){
		
	}
	
	private KThread[] getProducers(int numToCreate)
	{		
		KThread[] producers = new KThread[numToCreate];
		
		for(int i = 0; i < numToCreate; i++)
		{
			producers[i] = new KThread(new Producer(i, 10));
		}
		
		return producers;
	}
	
	private KThread[] getConsumers(int numToCreate)
	{
		KThread[] consumers = new KThread[numToCreate];
		
		for(int i = 0; i < numToCreate; i++)
		{
			consumers[i] = new KThread(new Consumer(i));
		}
		
		return consumers;
		
	}
	
	private void runThreads(KThread[] threads)
	{
		for(int i = 0; i < threads.length; i++)
		{
			threads[i].fork();
		}
	}
	
	/*
	 * Continuously adds a string n times to a synchronized 
	 * queue object, yielding after each addition.
	 */
	public class Producer implements Runnable{
		
		private int _id;
		private int _numIterations;
		
		public Producer(int id, int numIterations)
		{
			_id = id;
			_numIterations = numIterations;
		}
		
		public void run(){
			System.out.println("Producer # " + _id + " starting");
			
			System.out.println("Producer # " + _id + " ending");
		}
	}
	
	/*
	 * Continuously removes a string from a synchronized
	 * queue object, yielding after each removal.
	 */
	public class Consumer implements Runnable{
		
		private int _id;
		
		public Consumer(int id)
		{
			_id = id;
		}
		
		public void run(){
			System.out.println("Consumer # " + _id + " starting");
			
			System.out.println("Consumer # " + _id + " ending");
		}
	}
	
	/*
	 * A synchronized queue used for testing.
	 */
	public class SyncQueue{
		
		private Lock _lock;
		private ICondition _dataready;
		private Queue<Object> _queue;
		
		public SyncQueue(Lock lock, ICondition condition)
		{			
			_lock = lock;
			_dataready = condition;
			_queue = new LinkedList<Object>();	
		}
		
		public void AddToQueue(Object item){
			_lock.acquire();			//Get Lock
			_queue.add(item);	//Add item
			_dataready.wake();		//Signal any waiters
			_lock.release();			//Release Lock
		}

		public Object RemoveFromQueue(){
			_lock.acquire();			//Get Lock
			while(_queue.isEmpty()){	//need to use while loop because a thread earlier on ready queue might have emptied it
				_dataready.sleep();		//if nothing, sleep
			}
			Object item = _queue.remove();	//Get next item
			_lock.release();			//Release Lock
			return(item);
		}
	}
}
