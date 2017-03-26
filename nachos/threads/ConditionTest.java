package nachos.threads;

/*
 * A class that tests both Condition.java and Condition2.java
 * by using a synchronized queue that utilizes these condition 
 * variables. Tests to see if consumer/producer threads work
 * together using each condition variable implementation.
 */

import java.util.*;

public class ConditionTest extends KernelTestBase{
	
	public void TestCondition(){
		
		System.out.println("\n\nnENTERING CONDITION TEST\n\n");
		
		//Set up synchronized queue
		Lock queueLock = new Lock(); 					//lock for accessing the synchronized queue
		ICondition dataReady = new Condition(queueLock);		//condition representing when data is on the queue
		SyncQueue queue = new SyncQueue(queueLock, dataReady);	//the queue in common with producers and consumers
		
		//set up producer threads
		ProducerListener listener = new ProducerListener(); //keeps track of # producers still alive and producing
		KThread[] producers = getProducers(3, queue, listener);
		
		//set up consumer threads
		KThread[] consumers = getConsumers(3, queue, listener);
		
		runThreads(consumers);
		
		runThreads(producers);
		
		try{
			joinThreads(consumers);
		} catch(Exception ex){}
		
		System.out.println("\n\nEXITING CONDITION TEST\n\n");
	}
	
	public void TestCondition2(){
		
		System.out.println("\n\nENTERING CONDITION 2 TEST\n\n");
		
		Lock queueLock = new Lock(); 					//lock for accessing the synchronized queue
		ICondition dataReady = new Condition2(queueLock);		//condition representing when data is on the queue
		SyncQueue queue = new SyncQueue(queueLock, dataReady);	//the queue in common with producers and consumers
		
		//set up producer threads
		ProducerListener listener = new ProducerListener(); //keeps track of # producers still alive and producing
		KThread[] producers = getProducers(3, queue, listener);
		
		//set up consumer threads
		KThread[] consumers = getConsumers(3, queue, listener);
		
		runThreads(consumers);
		
		runThreads(producers);
		
		try{
			joinThreads(consumers);
		} catch(Exception ex){}
		
		System.out.println("\n\nEXITING CONDITION 2 TEST\n\n");
	}
	
	/*
	 * Creates producers that will add to the queue.
	 */
	private KThread[] getProducers(int numToCreate, SyncQueue queue, ProducerListener listener)
	{		
		KThread[] producers = new KThread[numToCreate];
		
		for(int i = 0; i < numToCreate; i++)
		{
			producers[i] = new KThread(new Producer(i, 5, queue, listener));
			
			listener.AddProducer();
		}
		
		return producers;
	}
	
	/*
	 * Creates consumers that will read from the queue when it's not empty.
	 */
	private KThread[] getConsumers(int numToCreate, SyncQueue queue, ProducerListener listener)
	{
		KThread[] consumers = new KThread[numToCreate];
		
		for(int i = 0; i < numToCreate; i++)
		{
			consumers[i] = new KThread(new Consumer(i, queue, listener));
		}
		
		return consumers;
		
	}
	
	/*
	 * A simplified version of a listener that keeps track of
	 * when all the producers are finished. When each producer finishes
	 * it notifies this listener object.
	 */
	public class ProducerListener
	{
		private int _numProducersAlive;
		private int _numItemsToBeProcessed;
		private int _numConsumersAlive;
		private Lock _lock;
			
		public ProducerListener()
		{
			_lock = new Lock();
		}
		
		public void AddProducer()
		{
			_numProducersAlive++;
		}
		
		public void AddConsumer()
		{
			_numConsumersAlive++;			
		}
		
		public void RemoveConsumer()
		{
			_numConsumersAlive--;				
		}
		
		public boolean AllProducersCompleted()
		{			
			return _numProducersAlive <= 0;			
		}
		
		public void AddItem(boolean isLastItemForProducer)
		{
			_lock.acquire();
			
			_numItemsToBeProcessed++;
			
			if(isLastItemForProducer)
			{
				_numProducersAlive--;
				
				System.out.println("Producers remaining: " + _numProducersAlive);
			}						
			
			_lock.release();
		}
		
		public boolean ContinueRemoving()
		{
			_lock.acquire();
			
			 boolean shouldContinue = (_numProducersAlive >= _numConsumersAlive) ||
					 (AllProducersCompleted() && _numItemsToBeProcessed >= _numConsumersAlive);
			 
			 if(shouldContinue)
			 {				 
				 _numItemsToBeProcessed--;
			 }
			 else
			 {
				 _numConsumersAlive--; 
			 }
			 
			_lock.release();
			
			return shouldContinue;
		}
		
	}
	
	/*
	 * Continuously adds a string n times to a synchronized 
	 * queue object, yielding after each addition.
	 */
	public class Producer implements Runnable{
		
		private int _id;
		private int _numIterations;
		private SyncQueue _queue;
		private ProducerListener _listener;
		
		public Producer(int id, int numIterations, SyncQueue queue, ProducerListener listener)
		{
			_id = id;
			_numIterations = numIterations;
			_queue = queue;
			_listener = listener;
		}
		
		public void run(){
			System.out.println("Producer # " + _id + " starting");
			
			addItemsToQueue();
			
			System.out.println("Producer # " + _id + " ending");
		}
		
		private void addItemsToQueue()
		{
			for(int i = 0; i < _numIterations; i++)
			{
				String message = "Producer # " + _id + " message # " + i;
				
				_listener.AddItem(i+1 == _numIterations);
				
				_queue.AddToQueue(message);			
				
				KThread.yield();
			}
		}
		
	}
	
	/*
	 * Continuously removes a string from a synchronized
	 * queue object, yielding after each removal.
	 */
	public class Consumer implements Runnable{
		
		private int _id;
		private SyncQueue _queue;
		private ProducerListener _listener;
		
		public Consumer(int id, SyncQueue queue, ProducerListener listener)
		{
			_id = id;
			_queue = queue;
			_listener = listener;
			_listener.AddConsumer();
		}
		
		public void run(){
			System.out.println("Consumer # " + _id + " starting");
			
			removeItemsFromQueue();
			
			System.out.println("Consumer # " + _id + " ending");
		}
		
		private void removeItemsFromQueue()
		{
			while(_listener.ContinueRemoving())
			{						
				String message = (String) _queue.RemoveFromQueue();
				
				System.out.println(message);
					
				KThread.yield();
			}
			
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
		
		public boolean IsEmpty()
		{
			return _queue.peek() == null;
		}				
		
		public void AddToQueue(Object item){
			_lock.acquire();			//Get Lock
			_queue.add(item);	//Add item
			_dataready.wake();		//Signal any waiters
			_lock.release();			//Release Lock
		}

		public Object RemoveFromQueue(){
			_lock.acquire();			//Get Lock
			while(_queue.isEmpty()){ //need to use while loop because a thread earlier on ready queue might have emptied it
				
				_dataready.sleep();		//if nothing, sleep
			}
			Object item = _queue.remove();	//Get next item
			_lock.release();			//Release Lock
			return(item);
		}
	}
}
