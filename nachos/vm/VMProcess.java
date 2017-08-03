package nachos.vm;

import java.util.concurrent.ThreadLocalRandom;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
	super();
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    	
    	invalidateTLBEntries();
    	
    	super.saveState();
    }

    /**
     * Invalidates TLB entries before context switch.
     */
    private void invalidateTLBEntries()
    {
    	Processor processor = Machine.processor();
    	
    	for(int i = 0; i < processor.getTLBSize(); i++)
    	{
    		TranslationEntry entry = processor.readTLBEntry(i);
    		
    		if(entry != null) entry.valid = false;
    	}
    }
    
    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	//super.restoreState();
    }

    
    @Override
    protected void initializeTranslations()
    {   	
		for (int i=0; i<this.numPages; i++)
		{
			int physPageNum = this.physMemPages[i].endIndex / pageSize;								
			
			VMKernel.putTranslation(this.processID, i,
					new TranslationEntry(i,physPageNum, true,false,false,false));
		}    	
		
		super.initializeTranslations();
    }
    
    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return	<tt>true</tt> if successful.
     */
    protected boolean loadSections() {
	return super.loadSections();
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
	super.unloadSections();
    }    

    /**
     * Handles a TLB Miss
     */
    private void handleTLBMiss()
    {
    	Machine.interrupt().disable();
    	
    	Processor processor = Machine.processor();
    	
    	int badVAddr = processor.readRegister(Processor.regBadVAddr);
    	
    	int vpn = badVAddr / pageSize;
    	
    	TranslationEntry entry = VMKernel.getTranslation(this.processID, vpn);    	
    	    	
    	//load the translation entry into processor's TLB
    	loadEntry(entry); 			
    	
    	Machine.interrupt().enable();   	    	
    }
    
    private void loadEntry(TranslationEntry entry)
    {
    	if(entry == null) return;
    	
    	Processor processor = Machine.processor();
    	
    	entry.used = false;
    	
    	for(int i = 0; i < processor.getTLBSize(); i++)
    	{
    		TranslationEntry existing = processor.readTLBEntry(i);
    		
    		//for now, just choose random index of TLB to overwrite
    		//TODO: add better TLB eviction policy
        	int randIndex = ThreadLocalRandom.current().nextInt(0, 4);        	
    		
    	    Machine.processor().writeTLBEntry(randIndex, entry);
    	}    	
    }
    
    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionTLBMiss:
		handleTLBMiss();
		break;
	default:
	    super.handleException(cause);
	    break;
	}
    }
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
}
