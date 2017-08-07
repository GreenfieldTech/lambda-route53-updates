package tech.greenfield.aws.route53;

public class SilentFailure extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SilentFailure(String message) {
		super(message);
	}

}
