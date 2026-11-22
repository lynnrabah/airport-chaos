import java.util.ArrayList;

public class Tokenizer {
	public static ArrayList<String> tokenize(String line){
		boolean flag=false;
		StringBuilder s=new StringBuilder();
		ArrayList<String> words=new ArrayList<String>();
		for (int i=0;i<line.length();i++) {
			if ((line.charAt(i)=='"')){
				flag=!flag;
			}
			s.append(line.charAt(i));
			if (line.charAt(i)==' ' && !flag) {
				if (s.length()>0) {
                    words.add(s.toString());
                    s.setLength(0);
                }
			}
			else {
				s.append(line.charAt(i));
			}
		}
		if (s.length()>0) {
            words.add(s.toString());
        }
		return words;
	}
}