package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;


/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	
	//a global inverted page table
	private InvertedPageTable _globalPageTable;  
	
	//a global core map with physical page # as index
	private CoreMapEntry[] _globalCoreMap;
	
	private SwapFileAccess _globalSwapFileAccess;
	
	//ensures only one eviction can  be happneing at once
	//private Lock _evictionLock;
	
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
    	super();  	    	
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) { 
    	super.initialize(args);
    	
       	//set up global inverted page table
    	this._globalPageTable = new InvertedPageTable();
    	
    	this._globalPageTable.initialize(8, 8, this);
    	
    	//set up global core map
    	Processor processor = Machine.processor();
    	
    	this._globalCoreMap = new CoreMapEntry[processor.getNumPhysPages()];        	    	
    }
    
    /**
     * Initializes swap file access object.
     */
    public void initializeSwapFileAccess()
    {    	
    	this._globalSwapFileAccess = new SwapFileAccess();
    	
    	this._globalSwapFileAccess.initialize();    	
    }
    
    public boolean swapExists()
    {
    	return this._globalSwapFileAccess != null;
    }
    
    /**
     * Loads the page from swap file on the file system and returns
     * the associated translation entry. 
     * @param pid
     * @param vpn
     * @return
     */
    public TranslationEntry loadPageFromSwap(int pid, int vpn)
    {   	
    	TranslationEntry entry = null;
    	
    	try
    	{
    		//enter criical section
    		this._pageAccessLock.acquire();
	    	
	    	//get a free page frame where the new page can go, mark
	    	//it as in-use
	    	PageFrame targetFrame = getNextFreeMemPage(pid);
	    	
	    	if(targetFrame == null)
	    	{
	    		int pageSize = Machine.processor().pageSize;
	    		
	    		int physPageNum = evictPage(pid);
	    		
	    		targetFrame = new PageFrame();
	    		
	    		targetFrame.startIndex = physPageNum * pageSize;
	    		
	    		targetFrame.endIndex = (physPageNum * pageSize) + pageSize - 1;
	    	}
	    	
	    	Lib.assertTrue(targetFrame != null);
	    	
	    	entry = this._globalSwapFileAccess.loadPage(pid, vpn, targetFrame);
	    	
	    	if(entry != null)
	    	{
	    		entry.valid = true;    		
	    		
	    		putTranslation(pid, entry);    		
	    	}
	    	else
	    	{        	
	    		//load failed - return the target frame to pool of free mem
	    		returnFreeMemPage(targetFrame);
	    	}    	
	
	    	//load attempt complete - mark the target page frame as not in use
	    	setPageNotInUse(targetFrame.startIndex / Machine.processor().pageSize);
    	}
    	finally
    	{
    		//exit critical section
    		this._pageAccessLock.release();
    	}
    	
    	return entry;
    }
    
    public void putTranslation(int processID, TranslationEntry entry)
    {
    	Lib.assertTrue(this._pageAccessLock.isHeldByCurrentThread());
    	
    	if(entry == null || entry.ppn < 0 || 
    			entry.ppn >= Machine.processor().getMemory().length)
    	{
    		//TODO: better handle bad translation entries
    		Lib.debug('s', "Kernel failed to put translation (PID " + processID + " VPN " + 
    				(entry == null ? "null" : entry.vpn) + ")");
    		
    		return;
    	}
    		
    	//add entry to global inverted page table
    	this._globalPageTable.put(processID, entry);
    	
    	//add entry to core map
    	this._globalCoreMap[entry.ppn] = new CoreMapEntry(processID, entry);
    	
    	Lib.debug('s', "Kernel putTranslation success (PID " + processID + " VPN " 
    			+ entry.vpn + ")"); 
    }
    
    public TranslationEntry getTranslation(int processID, int virtualPageNumber)
    {    	
    	return getTranslation(processID, virtualPageNumber, false);    	
    }    
    
    public TranslationEntry getTranslation(int processID, 
    		int virtualPageNumber, boolean markPageInUse)
    {
    	TranslationEntry entry;

  		entry = getTranslation(processID, virtualPageNumber, markPageInUse, true);
    			
    	return entry;    	
    }
    
    public TranslationEntry getTranslation(int processID, 
    		int virtualPageNumber, boolean markPageInUse, boolean shouldLock)
    {
    	TranslationEntry entry;
    	
    	try
    	{
    		if(shouldLock) this._pageAccessLock.acquire();
    	
    		entry = this._globalPageTable.get(processID, virtualPageNumber, markPageInUse);
    	}
    	finally
    	{
    		if(shouldLock) this._pageAccessLock.release();
    	}
    	
    	return entry;    	
    }

    /*
     * Called when main memory is full but a process needs a 
     * free page of memory. Frees up a page in main memory by
     * writing the page to the swap file (if applicable) and 
     * returns a PageFrame that represents the new empty slot in 
     * main memory.
     */        
    protected PageFrame freeUpMemory(int processID)
    {    	
    
    	Lib.debug('s', "Attempting to free up memory (PID " + processID + ")");
    	
    	int freePageNum = evictPage(processID);
    	
    	Lib.assertTrue(freePageNum >= 0, "Failed to free memory (PID " + processID + ")");    	
    	
    	int pageSize = Machine.processor().pageSize;   	
    	
		PageFrame frame = new PageFrame();
		
		frame.startIndex = freePageNum * pageSize;
		
		frame.endIndex = frame.startIndex + pageSize - 1;
    	
		Lib.debug('s', "Memory freed, returning (PID " + processID + ")");
		
    	return frame;
    }
    

    /**
     * Creates a new page and translation entry for that page..
     * @return
     */
    public TranslationEntry newPage(int pid, int vpn, boolean valid, boolean readOnly,
    		boolean used, boolean dirty)
    {       	    	
    	Lib.debug('s', "Kernel creating new page (PID " + pid + " VPN " + vpn + ")");
    	
    	TranslationEntry entry = null;
    	
    	try
    	{
    		this._pageAccessLock.acquire();
    		
    		int physPageNum = -1;
    		
	    	//obtain a free page of physical memory
	    	UserKernel.PageFrame freeMemPage = getNextFreeMemPage(pid);
	    	
	    	if(freeMemPage != null)
	    	{ 
	    		physPageNum = freeMemPage.endIndex / Machine.processor().pageSize;
	    	}
	    	else
	    	{
	    		physPageNum = evictPage(pid);
	    	}
	    	
	    	Lib.assertTrue(physPageNum >= 0, "Error creating new page: free page is null");	    	
	    	
	    	//create a translation entry
	    	entry = new TranslationEntry(vpn,physPageNum, 
	    			valid, readOnly, used, dirty);    	
	
	    	int pageSize = Processor.pageSize;
	    	
	    	byte[] memory = Machine.processor().getMemory();
	    	
	    	int paddr = entry.ppn*pageSize;
	    	
	    	if(paddr < 0 || paddr >= memory.length) return null;
	    	
	    	//clear out the bytes
	    	Arrays.fill(memory, paddr, paddr+pageSize, (byte) 0);
	    	
	    	//add the entry to the global inverted page table
	    	putTranslation(pid, entry);     
    	}
    	finally
    	{
    		this._pageAccessLock.release();
    	}
    	
    	return entry;
    }
    
    /*
     * Evicts a page from main memory and writes it to the swap file.
     * 
     * NOTE - this method marks the page as in-use after eviction is
     * complete. The calling code needs to ensure this is set as not
     * in use after it performs its critical operations.
     */
    private int evictPage(int processID)
    {   	    	
    	Lib.assertTrue(this._pageAccessLock.isHeldByCurrentThread());
    	
    	CoreMapEntry mapEntry = null;
    	
    	int physPageNum = -1;    			
    
    	while(mapEntry == null || mapEntry.entry == null || pageInUse(mapEntry.entry.ppn))
    	{
    		Lib.debug('s', "Attempting to evict page (PID " + processID + ")");
    		
    		//Currently chooses page at random. TODO: implement nth chance algorithm
    		physPageNum = ThreadLocalRandom.current().nextInt(0, _globalCoreMap.length);
    	
    		mapEntry = this._globalCoreMap[physPageNum];
    	}
    	
    	Lib.assertTrue(mapEntry != null && mapEntry.entry != null, 
    			"Error eviction page: entry is null");
    	
    	Lib.debug('s', "Evicted page from main memory (PID: " + mapEntry.processID + 
    			" VPN: " + mapEntry.entry.vpn + ")");
    	
    	//TODO: mark the page as in use so it can't be evicted by other processes
    	setPageInUse(mapEntry.entry.ppn);    	
    	
    	//write old page to the swap file
    	this._globalSwapFileAccess.writePage(mapEntry.processID, mapEntry.entry);
    	
    	//remove references to the page from core map and global inverted page table
    	this._globalCoreMap[physPageNum] = null;
    	
    	this._globalPageTable.remove(mapEntry.processID, mapEntry.entry.vpn);
    	
    	if(mapEntry.processID == processID) invalidateTLBEntry(mapEntry.entry.vpn);    
    	
    	return physPageNum;
    }
    
    /**
     * Invalidates TLB entry with the given vpn if it's in the TLB.
     */
    private void invalidateTLBEntry(int vpn)
    {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	Processor processor = Machine.processor();
    	
    	for(int i = 0; i < processor.getTLBSize(); i++)
    	{    		
    		TranslationEntry entry = processor.readTLBEntry(i);
    		    		
    		if(entry != null && entry.vpn == vpn)
    		{
    			entry.valid = false;
    			
    			processor.writeTLBEntry(i, entry);    		
    		}   		    		
    	}
    }
    
    /*
     * Deallocates memory for a process and cleans up
     * tranlsations, TLB, etc.
     */
    protected void deallocateProcessMemory(int processID)
    {    
    	Lib.assertTrue(Machine.interrupt().disabled());
    	
    	//free up any pages in swap file
    	
    	//remove translations from core map
    	TranslationEntry[] entries = this._globalPageTable.getAll(processID);
    	
    	if(entries == null) return;
    	
    	for(int i = 0; i < entries.length; i++)
    	{
    		if(entries[i] != null)
    		{
    			this._globalCoreMap[entries[i].ppn] = null;
    		}
    	}
    	
    	//remove translations from global page table
    	this._globalPageTable.removeAll(processID);
    }
    
    /**
     * Test this kernel.
     */	
    public void selfTest() {
	super.selfTest();
    }

    /**
     * Start running user programs.
     */
    public void run() {
	super.run();
    }
    
    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
    	
    	if(this._globalSwapFileAccess != null) this._globalSwapFileAccess.terminate();
    	
    	super.terminate();
    }

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';
    
    private class CoreMapEntry
    {
    	public int processID;
    	
    	public TranslationEntry entry;
    	
    	public CoreMapEntry(int pid, TranslationEntry entry)
    	{
    		this.processID = pid;
    		
    		this.entry = entry;
    	}
    }
    /** 
     * An inverted page table. The constructor takes as arguments
     * the estimated number of processes the OS should support at once,
     * and the number of estimated pages per process. These are used as
     * the initial capcity of the underlying hash table lookups.
     * 
     * @author luke
     *
     */
    private class InvertedPageTable
    {
    	private Hashtable<Integer, Hashtable<Integer, TranslationEntry>> _pageTable; 	    	
    	
    	private int _numPagesPerProcess;
    	
    	private Lock _pageTableLock;
    	
    	private VMKernel _kernel;
    	
    	public void initialize(int numProcesses, int numPagesPerProcess, VMKernel kernel)
    	{
    		this._numPagesPerProcess = numPagesPerProcess;
    		
    		this._pageTable = new Hashtable<Integer, 
    				Hashtable<Integer, TranslationEntry>>(numProcesses); 
    		
    		this._pageTableLock = new nachos.threads.Lock();
    		
    		this._kernel = kernel;
    	}
    	
    	/**
    	 * Adds a translation entry for the given process and virtual
    	 * page number. If the hashtable for the process doesn't yet exist,
    	 * create it.
    	 * 
    	 * @return
    	 */
    	public TranslationEntry put(int processID, TranslationEntry entry)
    	{
    		Lib.assertTrue(Machine.interrupt().disabled());
    		
    		try
    		{
    			this._pageTableLock.acquire();
	    		
    			Lib.debug('s', "Acquired page table lock - put (PID " + processID + ")");
    			
	    		Hashtable<Integer, TranslationEntry> processPageTable = 
	    				this._pageTable.get(processID);
	    		
	    		if(processPageTable == null)
	    		{
	    			processPageTable = new Hashtable<Integer, TranslationEntry>(this._numPagesPerProcess);
	    			
	    			this._pageTable.put(processID, processPageTable);
	    		}
	    		
	    		processPageTable.put(entry.vpn, entry);
    		}
    		finally
    		{
    			Lib.debug('s', "Releasing page table lock - put (PID " + processID + ")");
    			
    			this._pageTableLock.release();
    		}
    		
    		return entry;
    	}
    	
    	/**
    	 * Retrieves a translation entry based on process id and 
    	 * virtual page number. Returns null if either do not exist.
    	 * @param processID
    	 * @param virtualPageNumber
    	 * @return
    	 */
    	public TranslationEntry get(int processID, int virtualPageNumber, boolean markPageInUse)
    	{
    		TranslationEntry entry = null;
    		
    		//critical section
    		try
    		{
    			this._pageTableLock.acquire();    		
    			
    			Lib.debug('s', "Acquired page table lock - get (PID " + processID + ")");
	    		
	    		Hashtable<Integer, TranslationEntry> processPageTable 
	    			= this._pageTable.get(processID);
	    		
	    		if(processPageTable != null)
	    		{
	    			entry = processPageTable.get(virtualPageNumber);	    		
	    		}   			    
	    		
	    		if(entry != null && markPageInUse) this._kernel.setPageInUse(entry.ppn);
    		}
    		finally
    		{
    			Lib.debug('s', "Releasing page table lock - get (PID " + processID + ")");
    			
    			this._pageTableLock.release();
    		}
    		
    		return entry;
    	}
    	
    	/*
    	 * Gets all translation entries for a process.
    	 */
    	public TranslationEntry[] getAll(int processID)
    	{
    		Lib.assertTrue(Machine.interrupt().disabled());
    		
    		Hashtable<Integer, TranslationEntry> processPageTable
    			= this._pageTable.get(processID);
    		
    		if(processPageTable == null || processPageTable.values() == null) return null;
    		
    		Object[] values = processPageTable.values().toArray();
    		    		
    		return Arrays.copyOf(values, values.length, TranslationEntry[].class);
    	}
    	
    	public TranslationEntry remove(int processID, int virtualPageNumber)
    	{
    		Lib.assertTrue(Machine.interrupt().disabled());
    		
    		Hashtable<Integer, TranslationEntry> processPageTable 
    			= this._pageTable.get(processID);
		
    		if(processPageTable == null) return null;
		
    		return processPageTable.remove(virtualPageNumber);	
    	}
    	
    	/*
    	 * Removes all translations for a process.
    	 */
    	public void removeAll(int processID)
    	{
    		Lib.assertTrue(Machine.interrupt().disabled());
    		
    		this._pageTable.remove(processID);
    	}
    	
    }
    
    /**
     * A static data structure representing an API for the kernel to communicate
     * with the swap file on disk. All the translations, free page frames,
     * calls to the file system, etc, are encapsulated within this data 
     * structure.
     * @author luke
     *
     */
    private class SwapFileAccess
    {
    	//swap file to store swapped pages on disk for demand paging
    	private OpenFile _swapFile;
    	
    	private static final String _swapFileName = "lucos.swp";
    	
    	//keeps track of page frames in the swap file that are no longer
    	//in use and pages from main memory can be "paged out" to
    	private LinkedList<Integer> _freePageFrames;
    	
    	//a lookup of page frames and translation for a given process id
    	//nad virtual page number
    	private Hashtable<Integer, Hashtable<Integer, SwapEntry>> _swapLookup;
    	
    	//protects access to the swap file
    	private Lock _swapLock;    	    
    	
    	public void initialize()
    	{
        	//set up swap file
        	FileSystem fileSys = Machine.stubFileSystem();       	        	
        	
        	//if there's already a swapfile, find it and delete it
        	OpenFile existingSwap = fileSys.open(_swapFileName, false);
        	
        	if(existingSwap != null)
        	{
        		existingSwap.close();
        		
        		fileSys.remove(_swapFileName);
        	}
        	
        	this._swapFile = fileSys.open(_swapFileName, true); 
        	
        	this._freePageFrames = new LinkedList<Integer>();
        	
        	this._swapLookup = new Hashtable<Integer, Hashtable<Integer, SwapEntry>>();
        	
        	this._swapLock = new nachos.threads.Lock();      	        	       	        	
    	}    	 
    	
    	/**
    	 * Looks up the page in the swap file for the given process id and
    	 * virtual page number. Loads the page into main memory, and returns
    	 * the translation entry for the page.
    	 * 
    	 * @param pid process ID
    	 * @param vpn virtual page number
    	 * @param targetFrame frame of main memory to load into
    	 * @return
    	 */
    	public TranslationEntry loadPage(int pid, int vpn, PageFrame targetFrame)
    	{
    		Lib.debug('s', "Attempting to load from swap (PID " + pid + " VPN " + vpn + ")");
    		
    		//the translation to retrun (non-null if successful load)
    		TranslationEntry translation = null;
    		
    		//the swap entry that references where the page is in the swap file
    		SwapEntry entry = null;
    		
    		try
    		{   
    			Lib.debug('s', "Acquiring swap lock (PID " + pid + ")");
    			    			
    			//enter critical section
    			this._swapLock.acquire();
    				
    			Lib.debug('s', "Acquired swap lock (PID " + pid + ")");    			   			   			    		
    			
    			//get the swap lookup table for the process
    			Hashtable<Integer, SwapEntry> processSwapLookup 
					= this._swapLookup.get(pid);
		
	    		if(processSwapLookup == null)
	    		{
	    			Lib.debug('s', "Swap load failed (PID " + pid + " VPN " + vpn + ")");	    			
	    		}
	    		else
	    		{	
	    			//get the swap entry from the lookup table
		    		entry = processSwapLookup.get(vpn);  		    				    				    				    	
	    		}
	    		
	    		// if entry is null (could not find page in swap file)
	    		if(entry == null) Lib.debug('s', "Swap load failed - entry not found (PID " 
	    			+ pid + " VPN " + vpn + ")");	    				    			    		
	    		
	    		//load the entry, get the translation
	    		translation = load(pid, entry, targetFrame);    	
    		}
    		finally
    		{   			   			    			    		    			
    			Lib.debug('s', "Releasing swap lookup lock (PID " + pid + ")"); 
    			
    			//exit critical section
    			this._swapLock.release();
    		}  	    		    			    		
    		
    		if(translation == null)
    		{
    			Lib.debug('s', "Swap load failed (PID " + pid + " VPN " + vpn + ")");
    		}    
    		
    		return translation;
    	}
    	
    	/**
    	 * Loads page retrieved from swap file into memory.
    	 * @param entry
    	 */
    	private TranslationEntry load(int pid, SwapEntry entry, PageFrame targetFrame)
    	{
    		if(entry == null || entry.translation == null) return null;    		    	
    		
    		Lib.assertTrue(this._swapLock.isHeldByCurrentThread());
    		
    	    //get page to load from the swap file
    	    byte[] pageToLoad = new byte[Machine.processor().pageSize];    	    
    
    	    int bytesRead = _swapFile.read(entry.pageFrameIndex, 
    				pageToLoad, 0, pageToLoad.length);
    	    
    	    //check to make sure the read from swap was successful
    	    if(bytesRead != Machine.processor().pageSize) return null;			    	    
			    		
    		//get main memory from the processor
    		byte[] memory = Machine.processor().getMemory();
    		
    		int ppn = targetFrame.startIndex / Machine.processor().pageSize;
    		
    		//validate physical page number
    		if (ppn < 0 || ppn + Machine.processor().pageSize >= memory.length)
    		    return null;    		 
    		
    	    //load page from swap into main memory		
    	    System.arraycopy(pageToLoad, 0, memory, ppn, Machine.processor().pageSize);    	        	    		
    	        	    
    		return new TranslationEntry(entry.translation.vpn, ppn, true, 
    				entry.translation.readOnly,false, false);
    	}
    	
    	/**
    	 * Writes page from main memory to swap file.
    	 * @return
    	 */
    	public boolean writePage(int pid, TranslationEntry entry)
    	{
    		if(entry == null) return false;
    		    
    		Lib.debug('s', "Attempting to write to swap (PID " + pid + " VPN " + entry.vpn + ")");
    		
    		Hashtable<Integer, SwapEntry> processSwapLookup = null;
    		
    		SwapEntry swapEntry = null;
    		
    		boolean success = false;
    		
    		//enter critical section
    		try
    		{
    			Lib.debug('s', "Acquiring swap lock (write page)");
    			
    			this._swapLock.acquire();
    		
    			Lib.debug('s', "Acquired swap lock (write page)");
    			
	    		//get the swap lookup for the process, create if doesn't exist
	    		processSwapLookup = this._swapLookup.get(pid);
    		
	    		if(processSwapLookup == null)
	    		{
	    			processSwapLookup = new Hashtable<Integer, SwapEntry>();    			
	    			
	    			this._swapLookup.put(pid, processSwapLookup);
	    		}
    		
	    		//try to get the existing page frame index    		    	
	    		swapEntry = processSwapLookup.get(entry.vpn);	    		

	    		//if it doesn't exist, create a new one
		    	if(swapEntry == null) swapEntry = new SwapEntry(-1, entry);
	    		
		    	//write the page to swap
	    		success = writeToSwap(swapEntry);   		
	    		
	    		Lib.debug('s', "Write to swap " + (success ? "" : "un") + 
	    				"successful (PID " + pid + " VPN " + entry.vpn + ")");
	    		
	    		//if write successful, add entry to lookup
	    		if(success) processSwapLookup.put(entry.vpn,  swapEntry);    		    				    		
    		}
    		finally
    		{   	
    			Lib.debug('s', "Releasing swap lock (write page)");
    			
    			this._swapLock.release();
    			
    			Lib.debug('s', "Released swap lock (write page)");    
    		}    		
    		
    		return success;
    	}
    	
    	private boolean writeToSwap(SwapEntry swapEntry)
    	{
    		Lib.assertTrue(this._swapLock.isHeldByCurrentThread());
    		
    		if(swapEntry == null || swapEntry.translation == null) return false;
    		
    		//get main memory from the processor
    		byte[] memory = Machine.processor().getMemory();
    		
    		int ppn = swapEntry.translation.ppn;
    		
    		//validate physical page number
    		if (ppn < 0 || ppn >= memory.length)
    		    return false;    		 
    		
    		//get page to be written from main memory
    		byte[] pageToWrite = new byte[Machine.processor().pageSize];
    		    		
    		System.arraycopy(memory, ppn, pageToWrite, 0, Machine.processor().pageSize);
    		
    		int bytesWritten;    		    			    									
    			
	    	swapEntry.pageFrameIndex = swapEntry.pageFrameIndex >= 0 ? 
	    		swapEntry.pageFrameIndex : Math.max(_swapFile.length(), 0);
	    				
	    		//write page to swap
	    	bytesWritten = _swapFile.write(swapEntry.pageFrameIndex, 
	    		pageToWrite, 0, pageToWrite.length);
    		    		
    		return bytesWritten == Machine.processor().pageSize;
    	}
    	
    	
    	
    	public void terminate()
    	{
        	//delete swap file from disk
        	FileSystem fileSys = Machine.stubFileSystem();
        	
        	if(_swapFile != null) _swapFile.close();
        	
        	fileSys.remove(_swapFileName);
    	}
    	
    	private class SwapEntry
    	{
    		public int pageFrameIndex;
    		
    		public TranslationEntry translation;   
    		
    		public SwapEntry(int pageFrameIndex, TranslationEntry entry)
    		{
    			this.pageFrameIndex = pageFrameIndex;
    			
    			this.translation = entry;
    		}
    	}
    }
}
