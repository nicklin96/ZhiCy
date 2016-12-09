package addition;

public class Trie 
{
	private Node root;  
	   
    public Trie(){  
        root = new Node(" ");   
    }  
   
    public void insert(String[] words){  
        if(search(words) == true) return;  
          
        Node current = root;   
        for(int i = 0; i < words.length; i++){  
            Node child = current.subNode(words[i]);  
            if(child != null){   
                current = child;  
            } else {  
                 current.childList.add(new Node(words[i]));  
                 current = current.subNode(words[i]);  
            }  
            current.count++;  
        }   
        // Set isEnd to indicate end of the word  
        current.isEnd = true;  
    }  
    public boolean search(String[] words){  
        Node current = root;  
          
        for(int i = 0; i < words.length; i++){      
            if(current.subNode(words[i]) == null)  
                return false;  
            else  
                current = current.subNode(words[i]);  
        }  
        /*  
        * This means that a string exists, but make sure its 
        * a word by checking its 'isEnd' flag 
        */  
        if (current.isEnd == true) return true;  
        else return false;  
    }  
      
    public void deleteWord(String[] words){  
        if(search(words) == false) return;  
      
        Node current = root;  
        for(String c : words) {   
            Node child = current.subNode(c);  
            if(child.count == 1) {  
                current.childList.remove(child);  
                return;  
            } else {  
                child.count--;  
                current = child;  
            }  
        }  
        current.isEnd = false;  
    }  
      
    public static void main(String[] args) {  
        Trie trie = new Trie();
        String[] s1 = {"NNP","NNP"};
        String[] s2 = {"NNP","DT","NNP"};
        String[] s3 = {"NNP","NNP","NNP"};
        
        trie.insert(s2);  
        trie.insert(s3);  
      
        // testing deletion  
        System.out.println(trie.search(s1));  
        trie.insert(s1);  
        System.out.println(trie.search(s1));  
        trie.deleteWord(s2);
        System.out.println(trie.search(s2)); 
        
    }  
}
