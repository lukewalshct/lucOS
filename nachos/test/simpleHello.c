/*  simpleHello.c
 *	Program that prints out hello world
 * 	plus a small message. Useful for testing
 * 	opening/closing new processes in lucOS.
 */

int main(int argc, char *argv[])
{
    int i;
 
    for(i = 0; i < argc; i++)
    {
        int n = 0;

        while(argv[i][n] != 0)
        {
            printf("%c", argv[i][n]);

            n++;
        }

        printf("\n");
    }
}

