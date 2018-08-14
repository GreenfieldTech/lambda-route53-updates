package tech.greenfield.aws.route53;

public class NoIpException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public NoIpException(String message) {
		super(message);
	}
	
}
