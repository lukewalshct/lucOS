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
		Lock lock = new Lock();
		ICondition c = new Condition(lock);
		SyncQueue queue = new SyncQueue(lock, c);
	}
	
	public void TestCondition2(){
		
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
