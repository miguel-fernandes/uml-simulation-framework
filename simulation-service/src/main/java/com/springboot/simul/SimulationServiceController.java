package com.springboot.simul;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.springboot.simul.pojo.Request;
import com.springboot.simul.pojo.RequestProf;

@RestController
public class SimulationServiceController {
	@Autowired
	private SimulationServiceService simulService;

	@PostMapping(path = "/simulate", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<String> simulateModel(HttpServletRequest request) {

		String jsonString = request.getParameter("data");

		Gson gson = new Gson();
		Request requestObject = gson.fromJson(jsonString, Request.class);

		JSONObject obj = new JSONObject();
		String key = requestObject.getProject().getKey();
		String id = requestObject.getProject().getId();
		String artifact = requestObject.getArtifact();
		obj = obj.put(id, simulService.handleInput(key, artifact));

		return new ResponseEntity<>(obj.toString(), HttpStatus.OK);
	}

	@PostMapping("/submit")
	@ResponseBody
	public ResponseEntity<String> submitFile(HttpServletRequest request) {

		String jsonString = request.getParameter("data");

		Gson gson = new Gson();
		RequestProf requestObject = gson.fromJson(jsonString, RequestProf.class);

		String file = requestObject.getFile();
		String artifact = requestObject.getArtifact();
		simulService.handleInputProf(file, artifact);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
