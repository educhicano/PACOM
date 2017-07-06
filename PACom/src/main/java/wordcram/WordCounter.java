package wordcram;

/*
 Copyright 2010 Daniel Bernier

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import cue.lang.Counter;
import cue.lang.WordIterator;
import cue.lang.stop.StopWords;
import gnu.trove.set.hash.THashSet;

public class WordCounter {

	private StopWords cueStopWords;
	private Set<String> extraStopWords = new THashSet<String>();
	private boolean excludeNumbers;
	private int minWordLength = 4;

	public WordCounter() {
		this(null);
	}

	public WordCounter(int minWordLength) {
		this(null, minWordLength);
	}

	public WordCounter(StopWords cueStopWords) {
		this.cueStopWords = cueStopWords;
	}

	public WordCounter(StopWords cueStopWords, int minWordLength) {
		this.cueStopWords = cueStopWords;
		this.minWordLength = minWordLength;
	}

	public WordCounter withExtraStopWords(String extraStopWordsString) {
		String[] stopWordsArray = extraStopWordsString.toLowerCase().split(" ");
		extraStopWords = new THashSet<String>(Arrays.asList(stopWordsArray));
		return this;
	}

	public WordCounter shouldExcludeNumbers(boolean shouldExcludeNumbers) {
		excludeNumbers = shouldExcludeNumbers;
		return this;
	}

	public Word[] count(String text) {
		if (cueStopWords == null) {
			cueStopWords = StopWords.guess(text);
		}
		return countWords(text);
	}

	private Word[] countWords(String text) {
		Counter<String> counter = new Counter<String>();

		for (String word : new WordIterator(text)) {
			if (shouldCountWord(word)) {
				counter.note(word);
			}
		}

		List<Word> words = new ArrayList<Word>();

		for (Entry<String, Integer> entry : counter.entrySet()) {
			words.add(new Word(entry.getKey(), entry.getValue()));
		}

		return words.toArray(new Word[0]);
	}

	private boolean shouldCountWord(String word) {
		return hasEnoughLength(word) && !isStopWord(word) && !(excludeNumbers && isNumeric(word));
	}

	private boolean hasEnoughLength(String word) {
		return word.length() >= this.minWordLength && !extraStopWords.contains(word.toLowerCase());
	}

	private boolean isNumeric(String word) {
		try {
			Double.parseDouble(word);
			return true;
		} catch (NumberFormatException x) {
			return false;
		}
	}

	private boolean isStopWord(String word) {
		if (cueStopWords != null)
			return cueStopWords.isStopWord(word) || extraStopWords.contains(word.toLowerCase());
		return false;
	}

}
