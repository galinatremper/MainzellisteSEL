package de.unimainz.imbei.mzid;

import java.util.BitSet;

import javax.persistence.Column;
import javax.persistence.Entity;

@Entity
public class HashedField extends Field<BitSet>{
	@Column(columnDefinition = "text")
	private String value;
	
	private static BitSet String2BitSet(String b)
	{
		BitSet bs = new BitSet(b.length());
		for (int i = 0; i < b.length(); i++)
		{
			switch (b.charAt(i))
			{
			case '1' :
				bs.set(i);
			case '0' :
				break;
			default : // illegal value
				return null;
			}
		}
		return bs;
	}
	
	private static String BitSet2String(BitSet hash)
	{
		StringBuffer result = new StringBuffer(hash.size());
		for (int i = 0; i < hash.size(); i++)
		{
			if (hash.get(i))
				result.append("1");
			else
				result.append("0");
		}
		return result.toString();
	}
	
	public HashedField(BitSet b) {
		this.value = BitSet2String(b);
	}
	
	/** Constructor that accepts a String of 0s and 1s. */
	public HashedField(String b)
	{		
		this.value = b;
	}
	
	@Override
	public BitSet getValue() {
		return String2BitSet(this.value);
	}
	
	@Override
	public void setValue(BitSet hash) {
		this.value = BitSet2String(hash);
	}
	
	public HashedField clone()
	{		
		HashedField result = new HashedField(new String(this.value));
		return result;
	}
}
