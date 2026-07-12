package info.openrocket.core.aerodynamics.physicsaero.separation;
public record SeparatedRegion(String trackId,int onsetStation,int endStation,double onsetM,double severity,boolean reattachmentResolved,double confidence){
	public SeparatedRegion{if(trackId==null||trackId.isBlank()||onsetStation<0||endStation<onsetStation||severity<0||severity>1||confidence<0||confidence>1)throw new IllegalArgumentException("invalid separated region");}
}
