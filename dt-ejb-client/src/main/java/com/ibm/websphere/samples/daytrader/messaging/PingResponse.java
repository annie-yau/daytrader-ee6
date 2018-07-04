package com.ibm.websphere.samples.daytrader.messaging;

public class PingResponse {

		private String message;
		private String messageText;
		
		public PingResponse(){
		}
		public PingResponse(String message, String messageText){
			this.message = message;
			this.messageText = messageText;
		}
		public String getMessage() {
			return message;
		}
		public void setMessage(String message) {
			this.message = message;
		}
		public String getMessageText() {
			return messageText;
		}
		public void setMessageText(String messageText) {
			this.messageText = messageText;
		}
		
		
}
