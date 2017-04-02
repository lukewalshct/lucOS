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
	
	//condition variable that indicates a listener is ready
	//to receive message
	private ICondition _listenerReady;
	
	//condition variable that indicates a speaker is ready to
	//speak a message
	private ICondition _speakerReady;
	
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
    	
    	_listenerReady = new Condition2(_curMessageLock);
    	
    	_speakerReady = new Condition2(_curMessageLock);
    	
    	_messageSet = new Condition2(_curMessageLock);
    	
    	_messageReceived = new Condition2(_curMessageLock);
    	
    	_speakerSlotOpen = new Condition2(_curMessageLock);
    	
    	_listenerSlotOpen = new Condition2(_curMessageLock);
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
    	
    	if(_speakers > 0) _speakerSlotOpen.sleep();
    	
    	_speakers++;
    	
    	while(_listeners <= 0)
    	{
    		//wait until there is a listener that can take the message      	        	
    		_listenerReady.sleep();
    	}
    	
    	System.out.println("speaker setting word");
    	
    	//let any listeners know there's a speaker ready
    	_speakerReady.wake();
    	
    	//set the message
    	_curMessage = word;   
    	
    	//wake up the listener to let it knows the message is ready
    	_messageSet.wake();
    	
    	_messageReceived.sleep();
    	
    	_speakers--;
    	
    	_speakerSlotOpen.wake();
    	
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
    	
    	if(_listeners > 0) _listenerSlotOpen.sleep();
    	
    	_listeners++;
    	
    	while(_speakers <= 0)
    	{
    		//wait until speaker present
    		_speakerReady.sleep();
    	}    	
    	
    	//let any speakers know that a listener is now present
    	_listenerReady.wake();
    	
        //wait until message has been set
        _messageSet.sleep();	
    	
        System.out.println("getting msg");
        
    	//once speaker has set message, get the message
    	int msg = _curMessage;   	
    	
    	_messageReceived.wake();
    	
    	_listeners--;
    	
    	_listenerSlotOpen.wake();
    	
    	_curMessageLock.release();   	
    	
    	System.out.println(KThread.currentThread().getName() + " exiting listen");
    	
    	return msg;
    }
}
