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
    	
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	Lib.debug('s', "Preparing for context switch (PID " + this.processID + ")");
    	
    	invalidateTLBEntries();
    	
    	super.saveState();
    }

    /**
     * Invalidates TLB entries before context switch.
     */
    private void invalidateTLBEntries()
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	Lib.debug('t', "INVAILDATING TLB ENTRIES");
    	
    	Processor processor = Machine.processor();
    	
    	for(int i = 0; i < processor.getTLBSize(); i++)
    	{    		
    		TranslationEntry entry = processor.readTLBEntry(i);
    		    		
    		if(entry != null)
    		{
    			entry.valid = false;
    			
    			processor.writeTLBEntry(i, entry);    		
    		}   		    		
    	}
    	
    	Lib.debug('t', "TLB ENTRIES INVALIDATED");
    }
    
    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
    	Lib.debug('s', "Restoring context (PID " + this.processID + ")");
    	
    	boolean status = Machine.interrupt().disable();
    	
    	invalidateTLBEntries();
    	
    	Machine.interrupt().restore(status);
    }

    
    @Override
    protected void initializeTranslations()
    {   	
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	VMKernel kernel = (VMKernel) Kernel.kernel;
    	
		for (int i=0; i<this.numPages; i++)
		{
			int physPageNum = this.physMemPages[i].endIndex / pageSize;								
			
			kernel.putTranslation(this.processID,
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
    	
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	VMKernel kernel = (VMKernel) Kernel.kernel;
    	
    	invalidateTLBEntries();
    	
    	//if swap file doesn't yet exist, initialize it
    	if(!kernel.swapExists()) kernel.initializeSwapFileAccess();
    	
    	// load sections
    	for (int s=0; s<coff.getNumSections(); s++) {
    	    CoffSection section = coff.getSection(s);
    	    
    	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
    		      + " section (" + section.getLength() + " pages)");

    	    for (int i=0; i<section.getLength(); i++) {
    		
    	    	int vpn = section.getFirstVPN()+i;	
    	    	
    			TranslationEntry entry = kernel.newPage(this.processID, vpn, true, section.isReadOnly(),
    					false, false);

    			section.loadPage(i, entry.ppn);    		
    			
    			//load complete - set page to not in use
    			kernel.setPageNotInUseAndLock(entry.ppn);
    	    }
    	}
    	
    	//allocate first page for stack
    	TranslationEntry stackEntry = kernel.newPage(this.processID, 
    			this.getInitialSP() / pageSize, true, false, false, false);
    	
    	kernel.setPageNotInUseAndLock(stackEntry.ppn);
    	
    	return true;    	
    }
    
    /*
     * allocates a page for arguments, if they exist
     */
    /*
    @Override
    protected void allocateArgs()
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	int argVAddr = this.getArgV() / pageSize;
    	
    	VMKernel kernel = (VMKernel) Kernel.kernel;
    	
    	if(kernel.getTranslation(this.processID, argVAddr) == null)
    	{
    		TranslationEntry argEntry = kernel.newPage(this.processID, 
    				argVAddr, true, false, false, false);
    		
    		kernel.setPageNotInUse(argEntry.ppn);
    	}    	
    }*/
 
    /*
     * Deallocates memory upon process exit and cleans up
     * tranlsations, TLB, etc.
     */
    @Override
    protected void deallocateMemory()
    {
    	Machine.interrupt().disable();
    	
    	Lib.debug('s', "Process deallocating memory...");
    	
    	//invalidate TLB cache
    	invalidateTLBEntries();
    	
    	//return physical memory, page table entries, swap entries in use    	   
    	((VMKernel)Kernel.kernel).deallocateProcessMemory(this.processID);
    	
    	Machine.interrupt().enable();
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
    	    	
    	Lib.debug('t', "Handling TLB Miss, interrupt disabled (PID " + this.processID + ")");
    	
    	Processor processor = Machine.processor();
    	
    	int badVAddr = processor.readRegister(Processor.regBadVAddr);
    	
    	int vpn = badVAddr / pageSize;
    	
    	TranslationEntry entry = ((VMKernel)Kernel.kernel).getTranslation(this.processID, vpn);    	
    	    	
    	//if there's no entry or if it's invalid, handle page fault
    	if(entry == null || !entry.valid)
    	{
    		entry = handlePageFault(vpn);
    	}
    		
    	//load the translation entry into processor's TLB
    	loadTLBEntry(entry); 			
    	
    	Lib.debug('t', "Handled TLB Miss, enabling interrupt (PID " + 
    			this.processID + " VPN " + entry.vpn + ")");    	
    	
    	Machine.interrupt().enable();   	    	
    }
    
    /**
     * Handles page fault by calling VMKernel to get the page
     * from the swap file.
     * 
     * @param pid
     * @param vpn
     * @return
     */
    private TranslationEntry handlePageFault(int vpn)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());   	
    	    	
    	Lib.debug('s', "Handling page fault (PID " + this.processID + "VPN " + vpn + ")");
    	
    	VMKernel kernel = (VMKernel) Kernel.kernel;
    	
    	TranslationEntry entry = kernel.loadPageFromSwap(this.processID, vpn);
    	
    	//handle cases where the page does not exist in main mem or swap
    	if(entry == null)
    	{
    		//if stack page and within stack size limit, create new stack page
    		if(isStackPage(vpn)) 
    		{
    			entry = kernel.newPage(this.processID, vpn, true, false, false, false);
    			
    			kernel.setPageNotInUseAndLock(entry.vpn);
    		}
    			
    	}    	    	     	 
    	
    	//fatal error if entry is null (should exist or be created) TODO: kill process
    	Lib.assertTrue(entry != null, "Entry is null -- pid: " + this.processID + " vpn: " + vpn);    	
    	    	
    	Lib.debug('s', "Page fault handled (PID " + this.processID + "VPN " + vpn + ")");
    	
    	return entry;
    }
    
    private boolean isStackPage(int vpn)
    {
    	return (vpn <= (this.getInitialSP() / pageSize) && 
    			vpn >= (this.getInitialSP() / pageSize) - stackPages);
    }
    
    private void loadTLBEntry(TranslationEntry entry)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
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
    
    @Override
    protected TranslationEntry getTranslation(int vpn)
    {    	
    	return getTranslation(vpn, false);
    }
    
    @Override
    protected TranslationEntry getTranslation(int vpn, boolean nonEvictable)
    {    	
    	Lib.debug('s', "VMProcess retrieving translation, nonEvictable = " + nonEvictable);
    	
    	return ((VMKernel)Kernel.kernel).getTranslation(this.processID, vpn, nonEvictable);
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
