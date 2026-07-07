#!/usr/bin/env ruby

require "pry"
require "active_support/all"
require "json"

class FeatureTasksCreator
  PROJECT_DIR = Pathname.new(__FILE__).expand_path.join("../../../..")

  def initialize
    @name_id = {}
  end

  def run
    features_json = IO.read(PROJECT_DIR.join("tmp/features.json"))
    features = JSON.parse(features_json).with_indifferent_access

    task_template = IO.read(
      PROJECT_DIR.join("cider-ci", "generators", "feature-task-template.yml")
    )

    File.open(PROJECT_DIR.join("cider-ci", "generators", "feature-tasks.yml"), "w") do |f|
      features[:examples].map { |example|
        set_name(example)
      }.each { |example|
        f.write(task_template % example.as_json.transform_keys(&:to_sym))
      }
    end
  rescue => e
    puts e
    exit(-1)
  end

  private

  def set_name(example)
    name = example[:full_description].remove(example[:description]).strip
    @name_id[name] = 1 + (@name_id[name] || 0)
    example[:name] = name + format(" %02d", @name_id[name])
    example
  end
end

FeatureTasksCreator.new.run
