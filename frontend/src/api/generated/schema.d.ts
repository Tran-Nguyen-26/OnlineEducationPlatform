/**
 * OpenAPI-generated types placeholder.
 * Run `npm run api:gen` after the backend is running on :8080
 * to auto-generate this file from the live OpenAPI spec.
 */

export interface paths {
  '/auth/register': {
    post: {
      requestBody: {
        content: {
          'application/json': {
            email: string
            password: string
            fullName: string
            phone?: string | null
            dateOfBirth?: string | null
            organization?: string | null
            bio?: string | null
          }
        }
      }
      responses: {
        201: {
          content: {
            'application/json': {
              accessToken: string
              refreshToken: string
              tokenType: string
              expiresIn: number
              user: {
                id: string
                email: string
                fullName: string
                role: string
                mustChangePassword: boolean
              }
            }
          }
        }
        400: {
          content: {
            'application/json': {
              timestamp?: string
              status: number
              error: string
              message: string
              details?: Record<string, string>
            }
          }
        }
        409: {
          content: {
            'application/json': {
              timestamp?: string
              status: number
              error: string
              message: string
            }
          }
        }
      }
    }
  }
  '/auth/login': {
    post: {
      requestBody: {
        content: {
          'application/json': {
            email: string
            password: string
          }
        }
      }
      responses: {
        200: {
          content: {
            'application/json': {
              accessToken: string
              refreshToken: string
              tokenType: string
              expiresIn: number
              user: {
                id: string
                email: string
                fullName: string
                role: string
                mustChangePassword: boolean
              }
            }
          }
        }
      }
    }
  }
  '/auth/forgot-password': {
    post: {
      requestBody: {
        content: {
          'application/json': {
            email: string
          }
        }
      }
      responses: {
        200: {
          content: {
            'application/json': {
              message: string
              emailMasked: string
              cooldownSeconds: number
              expiresInSeconds: number
            }
          }
        }
        400: {
          content: {
            'application/json': {
              timestamp?: string
              status: number
              error: string
              message: string
              details?: Record<string, string>
            }
          }
        }
        429: {
          content: {
            'application/json': {
              timestamp?: string
              status: number
              error: string
              message: string
            }
          }
        }
      }
    }
  }
  '/auth/forgot-password/verify-otp': {
    post: {
      requestBody: {
        content: {
          'application/json': {
            email: string
            otpCode: string
          }
        }
      }
      responses: {
        200: {
          content: {
            'application/json': {
              resetToken: string
              expiresInSeconds: number
              message: string
            }
          }
        }
        400: {
          content: {
            'application/json': {
              timestamp?: string
              status: number
              error: string
              message: string
              details?: Record<string, string>
            }
          }
        }
      }
    }
  }
  '/auth/reset-password': {
    post: {
      requestBody: {
        content: {
          'application/json': {
            resetToken: string
            newPassword: string
            confirmPassword: string
          }
        }
      }
      responses: {
        200: {
          content: {
            'application/json': {
              message: string
            }
          }
        }
        400: {
          content: {
            'application/json': {
              timestamp?: string
              status: number
              error: string
              message: string
              details?: Record<string, string>
            }
          }
        }
      }
    }
  }
}

export type components = { schemas: Record<string, unknown> }
