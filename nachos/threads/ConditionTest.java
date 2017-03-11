package nachos.threads;

import java.util.*;

public class ConditionTest {

	
	/*
	 * A synchronized queue used for testing.
	 */
	public class SyncQueue{
		
		private Lock _lock;
		private Condition _dataready;
		private Queue<Object> _queue;
		
		public SyncQueue()
		{
			_queue = new LinkedList<Object>();
			_lock = new Lock();
			_dataready = new Condition(_lock);			
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
