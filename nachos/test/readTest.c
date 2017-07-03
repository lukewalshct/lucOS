/* readTest.c
 *	Program that tests reading a file in lucos
 *	by calling the read() system call.
 */

#include "syscall.h"

int testRead(char* fName, char* buffer, int size);

int main()
{
    char buffer[100];

    testRead("rTest1.txt", buffer, 10);
}

int testRead(char *fName, char* buffer, int size)
{
    int fHandle = open(fName);

    if(fHandle == -1)
    {
        printf("File does not exist");

        return -1;
    }
    else
    {
        int bytesRead = read(fHandle, buffer, 10);

        close(fHandle);

        printf(buffer);

        return bytesRead;
    }

}
