package tech.greenfield.aws.route53;

public class Route53UpdateResponse {
	private boolean status;
	private String message;

	public Route53UpdateResponse(boolean status, String message) {
		this.status = status;
		this.message = message;
	}

	public boolean getStatus() {
		return status;
	}
	
	public String getMessage() {
		return message;
	}
}
