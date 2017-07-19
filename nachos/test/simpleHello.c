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
        int n;

        for(n = 0; n < (sizeof(argv[i])/sizeof(argv[i][0])); n++)
        {
            printf("%c", argv[i][n]);
        }
    }
}

