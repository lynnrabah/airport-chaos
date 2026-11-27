import java.util.ArrayList;

public class Tokenizer {
	public static ArrayList<String> tokenize(String line) {
		ArrayList<String> words=new ArrayList<String>();
		StringBuilder s=new StringBuilder();
		boolean inQuotes=false;

		for (int i=0;i<line.length();i++) {
			char c=line.charAt(i);

			if (c=='"') {
				inQuotes=!inQuotes;            //flip the switch, don't keep the quote
			}
			else if (c==' ' && !inQuotes) {
				if (s.length()>0) {            //finish the word, only if it's not empty
					words.add(s.toString());
					s.setLength(0);
				}
			}
			else {
				s.append(c);                   //part of the current word
			}
		}

		if (inQuotes) {
			throw new IllegalArgumentException("Missing closing quote in: "+line);
		}
		if (s.length()>0) {                    //last word, which never met a space
			words.add(s.toString());
		}
		return words;
	}
}