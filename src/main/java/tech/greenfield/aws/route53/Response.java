package tech.greenfield.aws.route53;

public class Response{

	public static Route53UpdateResponse ok() {
		return new Route53UpdateResponse(true, "OK");
	}

	public static Route53UpdateResponse error(String message) {
		return new Route53UpdateResponse(false, "Error: " + message);
	}

}
