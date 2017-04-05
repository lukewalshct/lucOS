package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	
	//the current message; essentially is a buffer of length 1
	private int _curMessage;
	
	//condition variable that indicates a listener is present
	private ICondition _listenerPresent;
	
	//condition variable that indicates a speaker is present
	private ICondition _speakerPresent;
	
	private ICondition _speakerSlotOpen;
	
	private ICondition _listenerSlotOpen;
	
	private ICondition _messageSet;
	
	private ICondition _messageReceived;
	
	//lock for accessing _curMessage
	private Lock _curMessageLock;
	
	private int _speakers;
	
	private int _listeners;
	
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    	
    	_curMessageLock = new Lock();
    	
    	_listenerPresent = new Condition(_curMessageLock);
    	
    	_speakerPresent = new Condition(_curMessageLock);
    	
    	_messageSet = new Condition(_curMessageLock);
    	
    	_messageReceived = new Condition(_curMessageLock);
    	
    	_speakerSlotOpen = new Condition(_curMessageLock);
    	
    	_listenerSlotOpen = new Condition(_curMessageLock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	
    	System.out.println(KThread.currentThread().getName() + " entering speak");
    	
    	//get the lock for the message
    	_curMessageLock.acquire();
    	
    	//if there's already an "active" speaker, wait until it is finished
    	if(_speakers > 0) _speakerSlotOpen.sleep();
    	
    	//increment num speakers
    	_speakers++;
    	
    	//let any listeners know there's a speaker ready
    	_speakerPresent.wake();
    	
    	//wait until there is a listener that can take the message      	        	
    	_listenerPresent.sleep();
    	
    	System.out.println(KThread.currentThread().getName() + " speaker setting word");
    	
    	//set the message
    	_curMessage = word;   
    	
    	//wake up the listener to let it knows the message is ready
    	_messageSet.wake();
    	
    	//wait until message is received by listener
    	_messageReceived.sleep();
    
    	//OK to exit - decrement the num speakers and wake the next one
    	_speakers--;
    	
    	_speakerSlotOpen.wake();
    	
    	//release lock
       	_curMessageLock.release();   	
    	
    	System.out.println(KThread.currentThread().getName() + " exiting speak");
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	
    	System.out.println(KThread.currentThread().getName() + " entering listen");
    	
    	//get lock for the message
    	_curMessageLock.acquire();
    	
    	//if there's already an active listener, wait until it's finished
    	if(_listeners > 0) _listenerSlotOpen.sleep();
    	
    	//increment num listeners
    	_listeners++;
    	
    	//wait until speaker present
    	_speakerPresent.sleep();    	   	
    	
    	System.out.println("listener ready");
    	
    	//let any speakers know that a listener is now present
    	_listenerPresent.wake();
    	
        //wait until message has been set
        _messageSet.sleep();	
    	
        System.out.println(KThread.currentThread().getName() + " getting msg");
        
    	//once speaker has set message, get the message
    	int msg = _curMessage;   	
    	
    	//OK to exit critical section: decrement num listeners
    	_listeners--;
    	
    	//wake next listener
    	_listenerSlotOpen.wake();
    	
    	//let the speaker know the message was received
    	_messageReceived.wake();    	
    	
    	//exit critical section
    	_curMessageLock.release();   	
    	
    	System.out.println(KThread.currentThread().getName() + " exiting listen");
    	
    	//return message
    	return msg;
    }
}
