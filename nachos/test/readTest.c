/* readTest.c
 *	Program that tests reading a file in lucos
 *	by calling the read() system call.
 */

#include "syscall.h"

int testRead(char* fName, char* buffer, int size, char* expected);

void verifyResult(char* buffer, char* expected);
void throwReadError();

int main()
{
    char buffer[10];

    testRead("rTest1.txt", buffer, 10, "0123456789");
}

int testRead(char *fName, char* buffer, int size, char* expected)
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

        verifyResult(buffer, expected);

        return bytesRead;
    }

}

void verifyResult(char* buffer, char* expected)
{
    printf("\nRead test expected result:\n");

    printf(expected);
    
    printf("\nRead test actual result:\n");

    printf(buffer);

    printf("\n");

    int i;
 
    if(strlen(expected) != strlen(buffer)) throwReadError();

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
