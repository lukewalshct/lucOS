/* readTest.c
 *	Program that tests reading a file in lucos
 *	by calling the read() system call.
 */

#include "syscall.h"

int testRead(char* fName, int bufferLen, int size, char* expected);

void verifyResult(char* buffer, char* expected, int bytesRead);
void throwReadError();

int main()
{
    //normal read from a file
    testRead("rTest1.txt", 10, 10, "0123456789");

    //read from a file w/text, but size 0 - should read 0
    testRead("rTest1.txt", 0, 0, ""); 

    //read from file w/ no text - shoud read 0
    testRead("rTest2.txt", 10, 10, "");

    //read from file buffer smaller than read size - should
    testRead("rTest1.txt", 5, 10, "0123456789");
}

int testRead(char *fName, int bufferLen, int size, char* expected)
{
    char buffer[bufferLen];

    int fHandle = open(fName);

    if(fHandle == -1)
    {
        printf("File does not exist");

        return -1;
    }
    else
    {
        int bytesRead = read(fHandle, buffer, size);

        close(fHandle);

        verifyResult(buffer, expected, bytesRead);

        return bytesRead;
    }

}

void verifyResult(char* buffer, char* expected, int read)
{
    printf("\nRead test expected result:\n");

    printf(expected);
    
    printf("\nRead test actual result:\n");

    printf(buffer);

    printf("\n");

    int i;

    if(strlen(expected) != strlen(buffer) &&
        (strlen(expected) != read)) throwReadError();

    for(i = 0; i < strlen(expected); i++)
    {
        if(buffer[i] != expected[i]) throwReadError();        
    }     

}

void throwReadError()
{
    printf("!!!!READ TEST ERROR!!!");

    halt();
}
