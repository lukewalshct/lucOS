/*  workSim.c
 *	Program that simulates work of a
 * 	program by a simple while loop.
 */

int main(int argc, char *argv[])
{
    char c = argv[0][0];
 
    char badExit = argv[1][0];

    printf("starting worker process, id %c\n", c);
    
    int i = 0;

    //simulate work
    while(i++ < 9999999);

    if(badExit == '1') exit(-99);

    printf("ending worker process, id %c\n", c);   
}
