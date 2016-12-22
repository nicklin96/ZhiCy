package qa.evaluation;

import java.util.ArrayList;

import rdf.EntityMapping;
import rdf.PredicateMapping;

public class Binding {

	public boolean isVertexBinding = true;
	public boolean canMatchAllVetices = false;
	
	public ArrayList<EntityMapping> candEntMappings = null;
	public ArrayList<PredicateMapping> candPreMappings = null;
	public ArrayList<String> candLiterals = null;
	
	
}
