package org.example.http.app.dto;

import lombok.Value;

import java.util.Set;

@Value
public class CourseRequestDto {
  long id;
  String name;
  Set<String> tags;
}
