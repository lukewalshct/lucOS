/* writeTest.c
 * 	Program that tests reading a file in lucos 
 *	by calling the write() system call.
 */

#include "syscall.h"

void writeError();

int main()
{
    //open file on disk, create if doesn't exist
    int fHandle = creat("wTest1.txt");

    if(fHandle == -1) writeError();

    char *buffer = "Test text to write";

    write(fHandle, buffer, 15);
    
    close(fHandle);
}

void writeError()
{
    printf("ERROR IN WRITE TEST");

    halt();
}

