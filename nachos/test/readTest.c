/* readTest.c
 *	Program that tests reading a file in lucos
 *	by calling the read() system call.
 */

#include "syscall.h"

char *fName = "rTest1.txt";

int main()
{
    int fHandle = open(fName);

    if(fHandle == -1)
    {
        printf("File does not exist");
    }
    else
    {
        char buffer[100];

        int readSuccess = read(fHandle, buffer, 10);

        close(fHandle);

        printf(buffer);
    }
}
