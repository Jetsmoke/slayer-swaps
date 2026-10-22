package com.slayerswaps;

/**
 * Lets the development-only Slayer Swaps Testing plugin (in the test sources, so it isn't part of releases) pretend
 * to have a task, with a location and teleport. Unset, the real task and the saved choices are used.
 */
final class TaskOverride
{
	// The task name and Konar area (or null) to pretend to have, or null for the real task
	static volatile String[] task;
	// Pretend the task is done, to see the way back to the slayer master
	static volatile boolean done;
	// The location and teleport (Routes.key) to use for the pretend task, or null for the saved choices. Choosing on
	// the slayer helmet clears them, so the player's own saved choices are never overwritten by a test
	static volatile String location;
	static volatile String route;
	// Set while Slayer Swaps runs: reads the task again after the above change
	static volatile Runnable refresh;

	private TaskOverride()
	{
	}
}
