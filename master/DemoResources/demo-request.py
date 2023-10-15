import requests

def postDemoFiles():
	"""Sends a POST request. Returns job id
	"""
	URL = "http://localhost/submit"
	dockerfile = open("dockerfile", "rb")
	print(dockerfile.read())
	taskFile = open("entrypoint.sh", "rb")
	print(taskFile.read())
	files = {"dockerFile": dockerfile, "taskFiles": taskFile}
	response = requests.post(URL, files=files)
	dockerfile.close()
	taskFile.close()
	return response.text
  
if __name__ == "__main__":
	print("Demo ID:" + postDemoFiles())
	