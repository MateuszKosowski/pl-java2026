Feature: ONNX image classification
  As a client of the ai-service
  I want to classify an uploaded image with the MobileNetV2 ONNX model
  So that I receive a valid ImageNet label, a broad category and a confidence score

  Scenario: Classifying a dog photo returns a valid synset label
    Given the MobileNetV2 classification model is loaded
    When I classify the image "test-dog.jpg"
    Then the returned label is a non-blank entry present in synset.txt
    And the returned category is "dog"
    And the confidence is greater than 0
