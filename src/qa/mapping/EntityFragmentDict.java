package qa.mapping;

import java.util.HashMap;

import fgmt.EntityFragment;

public class EntityFragmentDict {
	public HashMap<String, EntityFragment> entityFragmentDictionary = new HashMap<String, EntityFragment>();
	
	public EntityFragment getEntityFragmentByName (String name) {
		if (name.startsWith("?")) {
			return null;
		}
		if (!entityFragmentDictionary.containsKey(name)) {
			String fgmt = EntityFragment.getEntityFgmtStringByName(name);
			if (fgmt != null) {
				entityFragmentDictionary.put(name, new EntityFragment(fgmt));
			}
			else {
				entityFragmentDictionary.put(name, null);
			}
		}
		return entityFragmentDictionary.get(name);

	}
}
