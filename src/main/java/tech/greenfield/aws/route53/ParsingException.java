package tech.greenfield.aws.route53;

public class ParsingException extends Exception {
	
	private static final long serialVersionUID = 5763904906449406323L;
	
	public ParsingException(Throwable e) {
		super(e);
	}

	public ParsingException(String message) {
		super(message);
	}

}
