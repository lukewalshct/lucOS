package nachos.threads;

/*
 * An interface for a condition variable. Not necessarily
 * required for Nachos to run, but enables easier testing
 * of Condition.java (implemented with semaphores) and 
 * Condition2.java.
 */
public interface ICondition {

	void sleep();
	
	void wake();
	
	void wakeAll();
}
