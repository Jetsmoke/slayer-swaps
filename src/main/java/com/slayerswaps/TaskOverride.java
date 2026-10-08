package com.slayerswaps;

/**
 * Lets the development-only Slayer Swaps Testing plugin (in the test sources, so it isn't part of releases) pretend
 * to have a task. Unset, the real task is used.
 */
final class TaskOverride
{
	// The task name and Konar area (or null) to pretend to have, or null for the real task
	static volatile String[] task;
	// Pretend the task is done, to see the way back to the slayer master
	static volatile boolean done;
	// Set while Slayer Swaps runs: reads the task again after the above change
	static volatile Runnable refresh;

	private TaskOverride()
	{
	}
}
