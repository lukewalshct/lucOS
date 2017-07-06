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

    char *buffer1 = "Test text to write";

    write(fHandle, buffer1, 15);

    char *buffer2 = "  next test text";

    write(fHandle, buffer2, strlen(buffer2));
    
    close(fHandle);
}

void writeError()
{
    printf("ERROR IN WRITE TEST");

    halt();
}

