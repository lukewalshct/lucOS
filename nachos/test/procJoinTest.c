/*   procJoinTest.c
 *	Program that tests join() syscall
 */

void delay(int i);

int main()
{
    printf("starting proc join test\n");

    delay(9999);    

    char *file = "workSim.coff";

    char *argv[] = {"0"};

    int childID = exec(file, 1, argv);

    delay(9999);

    printf("finishing proc join test\n");    
}

void delay(int i)
{
    int start = 0;

    while(start++ < i);
} 

