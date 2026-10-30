
export type Json = string | number | boolean | null | { [key: string]: Json | undefined } | Json[]

export type Database = {
  
  "public": {
          Tables: {
            "devices": {
                  Row: {
                    "created_at": string,"device_id": string,"last_synced_at": string,"schema_version": number,"user_id": string,"version_code": number
                  }
                  ComputedFields: never
                  Insert: {
                    "created_at"?: string,"device_id": string,"last_synced_at"?: string,"schema_version": number,"user_id": string,"version_code": number
                  }
                  Update: {
                    "created_at"?: string,"device_id"?: string,"last_synced_at"?: string,"schema_version"?: number,"user_id"?: string,"version_code"?: number
                  }
                  Relationships: [
                    
                  ]
                },"profiles": {
                  Row: {
                    "created_at": string,"display_name": string | null,"updated_at": string,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                    "created_at"?: string,"display_name"?: string | null,"updated_at"?: string,"user_id"?: string
                  }
                  Update: {
                    "created_at"?: string,"display_name"?: string | null,"updated_at"?: string,"user_id"?: string
                  }
                  Relationships: [
                    
                  ]
                },"sync_rows": {
                  Row: {
                    "deleted_at": number | null,"payload": Json | null,"server_updated_at": string,"sync_id": string,"table_name": string,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                    "deleted_at"?: number | null,"payload"?: Json | null,"server_updated_at"?: string,"sync_id": string,"table_name": string,"updated_at": number,"user_id"?: string
                  }
                  Update: {
                    "deleted_at"?: number | null,"payload"?: Json | null,"server_updated_at"?: string,"sync_id"?: string,"table_name"?: string,"updated_at"?: number,"user_id"?: string
                  }
                  Relationships: [
                    
                  ]
                },"trainer_links": {
                  Row: {
                    "accepted_at": string | null,"client_id": string | null,"created_at": string,"ended_at": string | null,"id": string,"invite_code": string,"label": string | null,"status": string,"trainer_id": string
                  }
                  ComputedFields: never
                  Insert: {
                    "accepted_at"?: string | null,"client_id"?: string | null,"created_at"?: string,"ended_at"?: string | null,"id"?: string,"invite_code"?: string,"label"?: string | null,"status"?: string,"trainer_id": string
                  }
                  Update: {
                    "accepted_at"?: string | null,"client_id"?: string | null,"created_at"?: string,"ended_at"?: string | null,"id"?: string,"invite_code"?: string,"label"?: string | null,"status"?: string,"trainer_id"?: string
                  }
                  Relationships: [
                    
                  ]
                }
          }
          Views: {
            "v_body_weight": {
                  Row: {
                    "epoch_day": number | null,"sync_id": string,"updated_at": number,"user_id": string,"weight_kg": number | null
                  }
                  ComputedFields: never
                  Relationships: [
                    
                  ]
                },"v_custom_exercise": {
                  Row: {
                    "difficulty": string | null,"equipment": string | null,"id": string,"instructions": string | null,"mechanics": string | null,"movement_pattern": string | null,"name": string | null,"primary_muscles": string | null,"secondary_muscles": string | null,"unilateral": boolean | null,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "difficulty"?: never,"equipment"?: never,"id"?: string | null,"instructions"?: never,"mechanics"?: never,"movement_pattern"?: never,"name"?: never,"primary_muscles"?: never,"secondary_muscles"?: never,"unilateral"?: never,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "difficulty"?: never,"equipment"?: never,"id"?: string | null,"instructions"?: never,"mechanics"?: never,"movement_pattern"?: never,"name"?: never,"primary_muscles"?: never,"secondary_muscles"?: never,"unilateral"?: never,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_exercise_usage": {
                  Row: {
                    "exercise_id": string | null,"last_used": number | null,"session_count": number | null,"user_id": string
                  }
                  ComputedFields: never
                  Relationships: [
                    
                  ]
                },"v_personal_record": {
                  Row: {
                    "achieved_at": number | null,"exercise_id": string | null,"reps": number | null,"session_id": string | null,"sync_id": string,"type": string | null,"updated_at": number,"user_id": string,"value": number | null,"weight_kg": number | null
                  }
                  ComputedFields: never
                  Insert: {
                           "achieved_at"?: never,"exercise_id"?: never,"reps"?: never,"session_id"?: never,"sync_id"?: string | null,"type"?: never,"updated_at"?: number | null,"user_id"?: string | null,"value"?: never,"weight_kg"?: never
                         }
                        Update: {
                           "achieved_at"?: never,"exercise_id"?: never,"reps"?: never,"session_id"?: never,"sync_id"?: string | null,"type"?: never,"updated_at"?: number | null,"user_id"?: string | null,"value"?: never,"weight_kg"?: never
                         }
                        Relationships: [
                    
                  ]
                },"v_plan": {
                  Row: {
                    "created_at": number | null,"goal": string | null,"is_active": boolean | null,"name": string | null,"split_type": string | null,"sync_id": string,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "created_at"?: never,"goal"?: never,"is_active"?: never,"name"?: never,"split_type"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "created_at"?: never,"goal"?: never,"is_active"?: never,"name"?: never,"split_type"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_plan_day": {
                  Row: {
                    "name": string | null,"plan_id": string | null,"position": number | null,"sync_id": string,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "name"?: never,"plan_id"?: never,"position"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "name"?: never,"plan_id"?: never,"position"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_plan_exercise": {
                  Row: {
                    "exercise_id": string | null,"plan_day_id": string | null,"position": number | null,"progression_rule": string | null,"rep_max": number | null,"rep_min": number | null,"rest_seconds": number | null,"sets": number | null,"starting_weight_kg": number | null,"superset_group": number | null,"sync_id": string,"target_rir": number | null,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "exercise_id"?: never,"plan_day_id"?: never,"position"?: never,"progression_rule"?: never,"rep_max"?: never,"rep_min"?: never,"rest_seconds"?: never,"sets"?: never,"starting_weight_kg"?: never,"superset_group"?: never,"sync_id"?: string | null,"target_rir"?: never,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "exercise_id"?: never,"plan_day_id"?: never,"position"?: never,"progression_rule"?: never,"rep_max"?: never,"rep_min"?: never,"rest_seconds"?: never,"sets"?: never,"starting_weight_kg"?: never,"superset_group"?: never,"sync_id"?: string | null,"target_rir"?: never,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_profile": {
                  Row: {
                    "birth_date_epoch_day": number | null,"body_weight_kg": number | null,"days_per_week": number | null,"equipment": string | null,"experience": string | null,"focus_areas": string | null,"goal": string | null,"height_cm": number | null,"limitations": string | null,"session_length_minutes": number | null,"sex": string | null,"sync_id": string,"units": string | null,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "birth_date_epoch_day"?: never,"body_weight_kg"?: never,"days_per_week"?: never,"equipment"?: never,"experience"?: never,"focus_areas"?: never,"goal"?: never,"height_cm"?: never,"limitations"?: never,"session_length_minutes"?: never,"sex"?: never,"sync_id"?: string | null,"units"?: never,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "birth_date_epoch_day"?: never,"body_weight_kg"?: never,"days_per_week"?: never,"equipment"?: never,"experience"?: never,"focus_areas"?: never,"goal"?: never,"height_cm"?: never,"limitations"?: never,"session_length_minutes"?: never,"sex"?: never,"sync_id"?: string | null,"units"?: never,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_scheduled_session": {
                  Row: {
                    "epoch_day": number | null,"notes": string | null,"plan_day_id": string | null,"session_id": string | null,"sync_id": string,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "epoch_day"?: never,"notes"?: never,"plan_day_id"?: never,"session_id"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "epoch_day"?: never,"notes"?: never,"plan_day_id"?: never,"session_id"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_session": {
                  Row: {
                    "ended_at": number | null,"is_deload": boolean | null,"notes": string | null,"perceived_difficulty": number | null,"plan_day_id": string | null,"started_at": number | null,"sync_id": string,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "ended_at"?: never,"is_deload"?: never,"notes"?: never,"perceived_difficulty"?: never,"plan_day_id"?: never,"started_at"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "ended_at"?: never,"is_deload"?: never,"notes"?: never,"perceived_difficulty"?: never,"plan_day_id"?: never,"started_at"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_session_exercise": {
                  Row: {
                    "exercise_id": string | null,"notes": string | null,"plan_exercise_id": string | null,"position": number | null,"progression_reason": string | null,"rest_seconds": number | null,"session_id": string | null,"substituted_from_exercise_id": string | null,"superset_group": number | null,"sync_id": string,"updated_at": number,"user_id": string
                  }
                  ComputedFields: never
                  Insert: {
                           "exercise_id"?: never,"notes"?: never,"plan_exercise_id"?: never,"position"?: never,"progression_reason"?: never,"rest_seconds"?: never,"session_id"?: never,"substituted_from_exercise_id"?: never,"superset_group"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Update: {
                           "exercise_id"?: never,"notes"?: never,"plan_exercise_id"?: never,"position"?: never,"progression_reason"?: never,"rest_seconds"?: never,"session_id"?: never,"substituted_from_exercise_id"?: never,"superset_group"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null
                         }
                        Relationships: [
                    
                  ]
                },"v_session_summary": {
                  Row: {
                    "completed_sets": number | null,"ended_at": number | null,"exercise_count": number | null,"is_deload": boolean | null,"notes": string | null,"plan_day_id": string | null,"plan_day_name": string | null,"plan_id": string | null,"started_at": number | null,"sync_id": string,"user_id": string,"volume_kg": number | null
                  }
                  ComputedFields: never
                  Relationships: [
                    
                  ]
                },"v_set_entry": {
                  Row: {
                    "completed_at": number | null,"notes": string | null,"position": number | null,"reps": number | null,"rir": number | null,"rpe": number | null,"session_exercise_id": string | null,"set_type": string | null,"sync_id": string,"updated_at": number,"user_id": string,"weight_kg": number | null
                  }
                  ComputedFields: never
                  Insert: {
                           "completed_at"?: never,"notes"?: never,"position"?: never,"reps"?: never,"rir"?: never,"rpe"?: never,"session_exercise_id"?: never,"set_type"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null,"weight_kg"?: never
                         }
                        Update: {
                           "completed_at"?: never,"notes"?: never,"position"?: never,"reps"?: never,"rir"?: never,"rpe"?: never,"session_exercise_id"?: never,"set_type"?: never,"sync_id"?: string | null,"updated_at"?: number | null,"user_id"?: string | null,"weight_kg"?: never
                         }
                        Relationships: [
                    
                  ]
                },"v_set_history": {
                  Row: {
                    "completed_at": number | null,"exercise_id": string | null,"notes": string | null,"position": number | null,"reps": number | null,"rir": number | null,"rpe": number | null,"session_exercise_id": string | null,"session_finished": boolean | null,"session_id": string | null,"session_started_at": number | null,"set_type": string | null,"sync_id": string,"user_id": string,"weight_kg": number | null
                  }
                  ComputedFields: never
                  Relationships: [
                    
                  ]
                }
          }
          Functions: {
            "accept_trainer_invite":
{ Args: { "code": string }; Returns: {
              "accepted_at": string | null,
"client_id": string | null,
"created_at": string,
"ended_at": string | null,
"id": string,
"invite_code": string,
"label": string | null,
"status": string,
"trainer_id": string
            }
                          SetofOptions: {
        from: "*"
        to: "trainer_links"
        isOneToOne: true
        isSetofReturn: false
      } },
"admin_stats":
{ Args: { "weeks"?: number }; Returns: Json
                           },
"can_read_user":
{ Args: { "owner_id": string }; Returns: boolean
                           },
"check_in_device":
{ Args: { "device_id": string,"schema_version": number,"version_code": number }; Returns: undefined
                           },
"create_trainer_invite":
{ Args: { "label"?: string }; Returns: {
              "accepted_at": string | null,
"client_id": string | null,
"created_at": string,
"ended_at": string | null,
"id": string,
"invite_code": string,
"label": string | null,
"status": string,
"trainer_id": string
            }
                          SetofOptions: {
        from: "*"
        to: "trainer_links"
        isOneToOne: true
        isSetofReturn: false
      } },
"delete_my_account":
{ Args: Record<PropertyKey, never>; Returns: undefined
                           },
"end_trainer_link":
{ Args: { "link_id": string }; Returns: undefined
                           },
"push_rows":
{ Args: { "rows": Json }; Returns: number
                           }
          }
          Enums: {
            [_ in never]: never
          }
          CompositeTypes: {
            [_ in never]: never
          }
        }
}

type DatabaseWithoutInternals = Omit<Database, '__InternalSupabase'>

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never
> = DefaultSchemaTableNameOrOptions extends { schema: keyof DatabaseWithoutInternals }
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
  ? (DefaultSchema["Tables"] & DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
      Row: infer R
    }
    ? R
    : never
  : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never
> = DefaultSchemaTableNameOrOptions extends { schema: keyof DatabaseWithoutInternals }
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
  ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
      Insert: infer I
    }
    ? I
    : never
  : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never
> = DefaultSchemaTableNameOrOptions extends { schema: keyof DatabaseWithoutInternals }
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
  ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
      Update: infer U
    }
    ? U
    : never
  : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never
> = DefaultSchemaEnumNameOrOptions extends { schema: keyof DatabaseWithoutInternals }
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
  ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
  : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never
> = PublicCompositeTypeNameOrOptions extends { schema: keyof DatabaseWithoutInternals }
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
  ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
  : never

export const Constants = {
  "public": {
          Enums: {
            
          }
        }
} as const
