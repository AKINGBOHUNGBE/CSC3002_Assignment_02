//M. M. Kuttel 2025 mkuttel@gmail.com
package barScheduling;

import java.util.Random;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/*
 Barman Thread class.
 */

public class Barman extends Thread {
	//===========changes===================//
	private final ConcurrentMap<DrinkOrder, Long> orderStartTimes = new ConcurrentHashMap<>(); // Hash map that stores all the start times for each patron
    // the total accumulated wait time (thread safe)
	private final AtomicLong totalWaitTime = new AtomicLong(0);
	//private final AtomicLong totalTurnaroundTime = new AtomicLong(0);
	// keeps count off all the orders made (thread safe)
    //private final AtomicInteger orderCount = new AtomicInteger(0);
	//=====================================//
	private CountDownLatch startSignal;
	private BlockingQueue<DrinkOrder> orderQueue;
	int schedAlg =0;
	int q=10000; //really big if not set, so FCFS
	private int switchTime;
	
	
	
	Barman(  CountDownLatch startSignal,int sAlg) {
		//which scheduling algorithm to use
		this.schedAlg=sAlg;
		if (schedAlg==1) this.orderQueue = new PriorityBlockingQueue<>(5000, Comparator.comparingInt(DrinkOrder::getExecutionTime)); //SJF
		else this.orderQueue = new LinkedBlockingQueue<>(); //FCFS & RR
	    this.startSignal=startSignal;
	}
	
	Barman(  CountDownLatch startSignal,int sAlg,int quantum, int sTime) { //overloading constructor for RR which needs q
		this(startSignal, sAlg);
		q=quantum;
		switchTime=sTime;
	}

	public void placeDrinkOrder(DrinkOrder order) throws InterruptedException {
		//==============changes==============//
		orderStartTimes.put(order, System.nanoTime()); // Record start time
		//===================================//
		orderQueue.put(order);
	}
	

	//================changes==================================//
	/*public long getAverageWaitTime() {
	int count = orderCount.get();
	return (count == 0) ? 0 : totalWaitTime.get() / count;
	}*/
	

	public long getTotWaitTime() {
		long totwait = totalWaitTime.get();
		return totwait;
	}
	
	//=========================================================//
	
	public void run() {
		int interrupts=0;
		try {
			DrinkOrder currentOrder;
			
			startSignal.countDown(); //barman ready
			startSignal.await(); //check latch - don't start until told to do so

			if ((schedAlg==0)||(schedAlg==1)) { //FCFS and non-preemptive SJF
				while(true) {
					currentOrder = orderQueue.take();
					
					
					System.out.println("---Barman preparing drink for patron " + currentOrder.toString());
					//==========================changes====================================//
					//timer stops 
					long startTime = orderStartTimes.remove(currentOrder); // Retrieve and remove
                	long waitNs = System.nanoTime() - startTime;
                	long waitMs = waitNs / 1_000_000; // Convert to milliseconds

					// Update metrics
					totalWaitTime.addAndGet(waitMs);
					//orderCount.incrementAndGet();

					//======================================================================//
					sleep(currentOrder.getExecutionTime()); //processing order (="CPU burst")
					System.out.println("---Barman has made drink for patron "+ currentOrder.toString());
					currentOrder.orderDone();
					sleep(switchTime);//cost for switching orders
				}
			}
			else { // RR 
				int burst = 0;
				int timeLeft = 0;
				System.out.println("---Barman started with q= " + q);

				while (true) {
					System.out.println("---Barman waiting for next order ");
					currentOrder = orderQueue.take();

					System.out.println("---Barman preparing drink for patron " + currentOrder.toString());
					//WaitTimeTracker.EndAndAccumulateTimer();
					burst = currentOrder.getExecutionTime();
					if (burst <= q) { //within the quantum
						//===========================changes======================//
						//timer stops 
						long startTime = orderStartTimes.remove(currentOrder); // Retrieve and remove
						long waitNs = System.nanoTime() - startTime;
						long waitMs = waitNs / 1_000_000; // Convert to milliseconds
						System.out.println(Long.toString(waitMs));

						// Update metrics
						totalWaitTime.addAndGet(waitMs);
						//orderCount.incrementAndGet();
						//=================================changes========================//

						sleep(burst); //processing complete order ="CPU burst"
						System.out.println("---Barman has made drink for patron " + currentOrder.toString());
						currentOrder.orderDone();
						
					} else {
						//============================changes====================================//
						//timer stops 
						long startTime = orderStartTimes.remove(currentOrder); // Retrieve and remove
						long waitNs = System.nanoTime() - startTime;
						long waitMs = waitNs / 1_000_000; // Convert to milliseconds
						System.out.println(Long.toString(waitMs));

						// Update metrics
						totalWaitTime.addAndGet(waitMs);
						//==========================================================================//

						sleep(q);

						timeLeft = burst - q;
						System.out.println("--INTERRUPT---preparation of drink for patron " + currentOrder.toString()
								+ " time left=" + timeLeft);
						interrupts++;
						currentOrder.setRemainingPreparationTime(timeLeft);
						orderQueue.put(currentOrder); //put back on queue at end
						//===================================changes ============================//
						orderStartTimes.put(currentOrder, System.nanoTime()); // Record start time
						//=======================================================================//

					}
					sleep(switchTime);//switching orders
				}
			}
			
			
				
		} catch (InterruptedException e1) {
			System.out.println("---Barman is packing up ");
			System.out.println("---number interrupts="+interrupts);
		}
	}
}


