package com.accgames.others;

import java.util.Random;

public class NumberGenerator {

	public static final Random numberGenerator = new Random();
	
	public static int nextInt(int n){
		return numberGenerator.nextInt(n);
	};
}